# 地圖地理校準

本次修正對象是 `assets/data/maps/maps.json` 的城池錨點，不是城名避讓。
世界地圖 72 點、全國 42 點、舊區域 6 點，共 120 個節點均納入。
保留城池 ID、勢力、道路端點及 travelMonths；存檔引用同一 ID，載入後使用新位置。

## 校準方法與精度

`tools/map-geography.json` 每列為 `[近似經度, 近似緯度, 底圖像素X, 底圖像素Y]`。
世界圖像素以 1920×1280、左上原點為參考，轉換為遊戲左下原點的正規化座標。
這是針對現有手繪底圖的人工地理配準，不是標準 GIS 投影，也不代表古城考古測量精度。
世界底圖的海岸、比例與部分島形有藝術化變形；經緯度供區域辨識，像素供實際落點。
先對到正確國家／地區、海岸與島嶼，再檢查海面，不能單靠「最近一塊陸地」決定地點。

42 城與六城底圖只有示意山河，沒有可校準的完整海岸線。兩者按近似經緯度線性配置，
修正東西南北關係；不把裝飾性河線當成精確歷史河道。它們仍是區域示意圖。

成都代表四川盆地西側成都地區；江州代表重慶；會稽代表浙江紹興，位於吳郡以南的大陸。
國內城、丸都城按集安附近定位，不按舊 ID 所暗示的平壤、首爾定位。

## 舊 ID 與現有地名

定位以 `assets/i18n/ui_zh_Hant.xml` 的目前名稱為準，不能由英文 ID 猜地點。

| 舊 ID | 現有地名／代表地區 |
| --- | --- |
| pyongyang / seoul | 國內城／丸都城：吉林集安附近，二者很接近，不為文字排版而分開地理座標 |
| karakorum | 彈汗山：陰山以東、今冀北／內蒙古南部代表點，遺址定位有不確定性 |
| samarkand / bukhara | 康居／烏孫：中亞錫爾河地區／伊塞克湖與伊犁地區代表點 |
| delhi / mumbai / pataliputra / varanasi / lanka | 富樓沙（白沙瓦）／塔克西拉／秣菟羅／迦畢試／巴克特拉（巴爾赫） |
| persepolis / babylon / isfahan / hormuz | 蘇薩／塞琉西亞／埃克巴坦那（哈馬丹）／赫卡通皮洛斯（達姆甘附近） |
| london / paris / cologne / prague / stockholm | 辛布里（日德蘭）／馬科曼尼（波希米亞）／夸迪（摩拉維亞）／赫蒙杜里（中德）／哥特蘭 |
| meroe / timbuktu / gao / great_zimbabwe | 阿杜利斯／傑內－傑諾／諾克／林波波河谷 |
| tenochtitlan / cahokia / cusco / chichen_itza | 喬盧拉／霍普韋爾（俄亥俄）／卡瓦奇（秘魯納斯卡附近）／蒙特阿爾班 |
| hawaii / tahiti / aotearoa / uluru | 東加／斐濟／萬那杜／新喀里多尼亞 |
| kyiv / moscow / malacca | 奧爾比亞（黑海西北岸）／塔奈斯（亞速海北岸）／吉打 |

邪馬台採九州代表點，史學上亦有畿內說，不能宣稱已確定其古都遺址。
圖勒暫沿用冰島代表點；古典文獻所指位置有爭議。白令海岸、部族、河谷與群島
使用區域代表點，不等同於唯一城市。葉調沿用爪哇代表點，其古名比定也非無爭議。
本次不改劇本年代或上述名稱。

## 地理參考

- [紹興位置](https://en.wikipedia.org/wiki/Shaoxing)：杭州灣南岸、浙江東北。
- [四川盆地](https://en.wikipedia.org/wiki/Sichuan_Basin)：成都在西、重慶在東。
- [UNESCO 高句麗王城](https://whc.unesco.org/en/list/1135)：國內城、丸都城在集安。
- [UNESCO 塔克西拉](https://whc.unesco.org/en/list/139)：巴基斯坦旁遮普 Rawalpindi 地區。
- [Encyclopaedia Iranica: Capital Cities](https://www.iranicaonline.org/articles/capital-cities/)：伊朗與中亞古都比定。
- [Pleiades](https://pleiades.stoa.org/places)：地中海與西亞古地名參考。
- [Perseus: Quadi](https://www.perseus.tufts.edu/hopper/text?doc=Perseus%3Atext%3A1999.04.0064%3Aalphabetic+letter%3DQ%3Aentry+group%3D1%3Aentry%3Dquadi-geo)：摩拉維亞及多瑙河北側地區。
- [UNESCO 霍普韋爾](https://whc.unesco.org/en/list/1689)。
- [邪馬台位置研究](https://www.jstage.jst.go.jp/article/tja1948/42/2/42_2_93/_article/-char/en)：位置存在不同學說。

上述來源支持地區與名稱比定；JSON 中的小數為遊戲校準近似值，非逐點來源直接提供的精密測量值。

## 重現與驗證

```text
python tools/calibrate-map-geography.py
python tools/calibrate-map-geography.py --check-art
python tools/calibrate-map-geography.py --plots build/geography-qa
python tools/calibrate-map-geography.py --apply
```

預設只驗證；`--apply` 才機械更新座標。`--root` 可指定另一份同結構 checkout。
繪圖需 Pillow、Matplotlib，產物留在 build 下，不能取代實際遊戲畫面驗收。
底圖像素 QA 是海面錯置的輔助偵測，不能證明歷史位置絕對正確。
