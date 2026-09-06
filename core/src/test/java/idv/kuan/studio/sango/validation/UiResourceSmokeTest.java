package idv.kuan.studio.sango.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import idv.kuan.studio.sango.ui.widget.MapCameraState;

/**
 * XML、i18n、靜態 Actor ID 與純 Java 相機測試；不宣稱取代實際 UI 排版與輸入測試。
 */
public final class UiResourceSmokeTest {
    private static final Pattern TEXT_REFERENCE = Pattern.compile("\\$\\{text:([a-zA-Z0-9_]+)}");
    private static final Pattern LITERAL_ACTOR_REFERENCE = Pattern.compile(
        "(?:button|label|onClick|getActor|attachModalMask)\\(\\s*\"([a-zA-Z0-9_]+)\""
    );
    private static final Pattern LITERAL_I18N_REFERENCE = Pattern.compile("\\btext\\(\\s*\"([a-zA-Z0-9_]+)\"");
    private static final Pattern SCREEN_XML = Pattern.compile("registerXml\\(\"(ui/[a-zA-Z0-9_]+\\.xml)\"\\)");
    private static int checks;

    private UiResourceSmokeTest() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("需要 assets 目錄路徑。");
        }
        Path assetsPath = Path.of(arguments[0]).toAbsolutePath().normalize();
        Map<String, String> localizedTexts = loadLocalizedTexts(assetsPath);
        validateFontCharacterCoverage(assetsPath, localizedTexts);
        Map<String, Set<String>> actorIdsByXml = new HashMap<>();
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Schema simpleUiSchema = schemaFactory.newSchema(
            UiResourceSmokeTest.class.getResource("/simpleui.xsd")
        );
        try (Stream<Path> xmlPaths = Files.list(assetsPath.resolve("ui"))) {
            for (Path xmlPath : xmlPaths.filter(path -> path.toString().endsWith(".xml")).toList()) {
                simpleUiSchema.newValidator().validate(new StreamSource(xmlPath.toFile()));
                checks++;
                Set<String> actorIds = readActorIds(xmlPath);
                actorIdsByXml.put("ui/" + xmlPath.getFileName(), actorIds);
                Matcher textReferences = TEXT_REFERENCE.matcher(Files.readString(xmlPath));
                while (textReferences.find()) {
                    check(localizedTexts.containsKey(textReferences.group(1)),
                        "XML 引用缺少 i18n：" + xmlPath.getFileName() + " / " + textReferences.group(1));
                }
            }
        }
        Path sourceRoot = assetsPath.getParent().resolve("core/src/main/java");
        try (Stream<Path> javaPaths = Files.walk(sourceRoot)) {
            for (Path javaPath : javaPaths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(javaPath);
                Matcher xmlReference = SCREEN_XML.matcher(source);
                if (xmlReference.find()) {
                    Set<String> actorIds = actorIdsByXml.get(xmlReference.group(1));
                    check(actorIds != null, "Screen 必須有實際 XML：" + javaPath.getFileName());
                    Matcher actorReferences = LITERAL_ACTOR_REFERENCE.matcher(source);
                    while (actorReferences.find()) {
                        check(actorIds.contains(actorReferences.group(1)),
                            "Java 引用缺少 Actor ID：" + javaPath.getFileName() + " / " + actorReferences.group(1));
                    }
                }
                Matcher textReferences = LITERAL_I18N_REFERENCE.matcher(source);
                while (textReferences.find()) {
                    check(localizedTexts.containsKey(textReferences.group(1)),
                        "Java 引用缺少 i18n：" + javaPath.getFileName() + " / " + textReferences.group(1));
                }
            }
        }
        Set<String> factionScreenIds = actorIdsByXml.get("ui/new_game.xml");
        for (int i = 1; i <= 6; i++) {
            check(factionScreenIds.contains("faction_" + i + "_button"), "六個勢力按鈕 ID");
        }
        String cityXml = Files.readString(assetsPath.resolve("ui/city.xml"));
        check(cityXml.indexOf("id=\"city_settings_button\"") < cityXml.indexOf("id=\"return_map_button\""),
            "內政設定按鈕必須在返回地圖左方");
        check(actorIdsByXml.get("ui/strategic_map.xml").contains("map_zoom_in_button"), "地圖縮放按鈕");
        check(actorIdsByXml.get("ui/month_report.xml").contains("month_report_scroll_host"), "月報捲動容器");
        check(!Files.exists(sourceRoot.resolve("idv/kuan/studio/sango/FirstScreen.java")), "移除未使用樣板 Screen");
        check(!Files.exists(sourceRoot.resolve("idv/kuan/studio/sango/ui/PrototypeCampaignScreen.java")),
            "淘汰 Prototype Screen 不可回歸");
        check(!Files.exists(assetsPath.resolve("ui/prototype_campaign.xml")), "淘汰 Prototype XML 不可回歸");
        validateCamera();
        System.out.println("Sango UI resources and map camera: PASS; checks=" + checks);
    }

    private static void validateFontCharacterCoverage(
        Path assetsPath,
        Map<String, String> localizedTexts
    ) throws Exception {
        String i18nSource = Files.readString(assetsPath.resolve("i18n/ui_zh_Hant.xml"));
        check(!i18nSource.contains("&#"),
            "SimpleUI 使用的 LibGDX XmlReader 不支援 numeric XML entity；請勿用 &#10; 表示換行");

        Set<Integer> availableCodePoints = new HashSet<>();
        Files.readString(assetsPath.resolve("characters/default.txt"))
            .codePoints()
            .forEach(availableCodePoints::add);

        for (Map.Entry<String, String> entry : localizedTexts.entrySet()) {
            validateHanCharacters(
                availableCodePoints,
                entry.getValue(),
                "i18n:" + entry.getKey()
            );
        }

        try (Stream<Path> dataPaths = Files.walk(assetsPath.resolve("data"))) {
            for (Path dataPath : dataPaths.filter(path -> path.toString().endsWith(".json")).toList()) {
                validateHanCharacters(
                    availableCodePoints,
                    Files.readString(dataPath),
                    "data:" + assetsPath.relativize(dataPath)
                );
            }
        }
    }

    private static void validateHanCharacters(
        Set<Integer> availableCodePoints,
        String text,
        String sourceDescription
    ) {
        text.codePoints().forEach(codePoint -> {
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                check(availableCodePoints.contains(codePoint),
                    "中文字型字元集缺少「" + new String(Character.toChars(codePoint))
                        + "」：" + sourceDescription);
            }
        });
    }

    private static Map<String, String> loadLocalizedTexts(Path assetsPath) throws Exception {
        Document document = readXml(assetsPath.resolve("i18n/ui_zh_Hant.xml"));
        Map<String, String> texts = new HashMap<>();
        NodeList entries = document.getElementsByTagName("entry");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            check(texts.put(entry.getAttribute("name"), entry.getAttribute("value")) == null,
                "i18n Key 不可重複：" + entry.getAttribute("name"));
        }
        return texts;
    }

    private static Set<String> readActorIds(Path xmlPath) throws Exception {
        Set<String> actorIds = new HashSet<>();
        NodeList elements = readXml(xmlPath).getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (element.hasAttribute("id")) {
                check(actorIds.add(element.getAttribute("id")),
                    "同一 XML 的 Actor ID 不可重複：" + xmlPath.getFileName() + " / " + element.getAttribute("id"));
            }
        }
        return actorIds;
    }

    private static Document readXml(Path xmlPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(xmlPath.toFile());
    }

    private static void validateCamera() {
        MapCameraState camera = new MapCameraState();
        camera.configure(1040f, 428f, 4200f, 2800f);
        camera.centerOn(2100f, 1400f);
        check(close(camera.worldX(camera.screenX(1600f)), 1600f), "相機 X 座標反算");
        check(close(camera.worldY(camera.screenY(1200f)), 1200f), "相機 Y 座標反算");
        float anchorWorldX = camera.worldX(650f);
        float anchorWorldY = camera.worldY(300f);
        camera.zoomAt(1.4f, 650f, 300f);
        check(close(camera.worldX(650f), anchorWorldX) && close(camera.worldY(300f), anchorWorldY),
            "遠離邊界時縮放錨點保持一致");
        float beforePanWorldX = camera.worldX(520f);
        camera.pan(140f, 0f);
        check(close(camera.worldX(520f), beforePanWorldX - 100f), "拖曳依縮放換算距離");
        camera.fitAll();
        check(camera.screenX(0f) >= -0.01f && camera.screenX(4200f) <= 1040.01f,
            "全圖模式包含水平邊界");
        check(camera.screenY(0f) >= -0.01f && camera.screenY(2800f) <= 428.01f,
            "全圖模式包含垂直邊界");
        camera.zoomAt(999f, 520f, 214f);
        check(close(camera.getZoom(), MapCameraState.MAXIMUM_ZOOM), "相機放大上限");
        camera.pan(1000000f, 1000000f);
        check(camera.worldX(0f) >= -0.01f && camera.worldY(0f) >= -0.01f, "相機不可平移出負向邊界");
        camera.pan(-2000000f, -2000000f);
        check(camera.worldX(1040f) <= 4200.01f && camera.worldY(428f) <= 2800.01f,
            "相機不可平移出正向邊界");
        float validZoom = camera.getZoom();
        camera.zoomAt(Float.NaN, 0f, 0f);
        camera.pan(Float.POSITIVE_INFINITY, 0f);
        check(close(camera.getZoom(), validZoom), "非有限輸入不可污染相機狀態");
        boolean invalidSizeRejected = false;
        try {
            camera.configure(0f, 428f, 4200f, 2800f);
        } catch (IllegalArgumentException expected) {
            invalidSizeRejected = true;
        }
        check(invalidSizeRejected, "零尺寸相機必須拒絕");
    }

    private static boolean close(float firstValue, float secondValue) {
        return Math.abs(firstValue - secondValue) < 0.02f;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
