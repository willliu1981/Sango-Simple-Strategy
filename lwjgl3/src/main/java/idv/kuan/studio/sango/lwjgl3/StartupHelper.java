/*
 * Copyright 2020 damios
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package idv.kuan.studio.sango.lwjgl3;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * 處理 LWJGL3 在 macOS 的 First Thread 啟動需求，並避開 Windows 非 ASCII
 * 使用者目錄可能造成的 native library 解壓問題。
 */
public final class StartupHelper {
    private static final String JVM_RESTARTED_ARGUMENT = "jvmIsRestarted";

    private StartupHelper() {
    }

    /**
     * 必要時以正確 JVM 參數重新啟動程式，並將新行程輸出導回目前終端。
     *
     * @return 已啟動新 JVM 時為 {@code true}；呼叫端應立即結束目前 main 流程
     */
    public static boolean startNewJvmIfRequired() {
        return startNewJvmIfRequired(true);
    }

    /**
     * 必要時以正確 JVM 參數重新啟動程式。
     *
     * @param redirectOutput 是否等待新行程並沿用目前標準輸出與錯誤輸出
     * @return 已啟動新 JVM 時為 {@code true}
     */
    public static boolean startNewJvmIfRequired(boolean redirectOutput) {
        String operatingSystemName = System.getProperty("os.name", "").toLowerCase();
        if (!operatingSystemName.contains("mac")) {
            configureWindowsNativeTempDirectory(operatingSystemName);
            return false;
        }

        if (!System.getProperty("org.graalvm.nativeimage.imagecode", "").isEmpty()) {
            return false;
        }

        long processId = ProcessHandle.current().pid();
        if ("1".equals(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + processId))) {
            return false;
        }

        if ("true".equals(System.getProperty(JVM_RESTARTED_ARGUMENT))) {
            System.err.println("無法確認 macOS JVM 是否使用 -XstartOnFirstThread；將沿用目前行程啟動。");
            return false;
        }

        List<String> restartArguments = buildRestartArguments(processId);
        if (restartArguments.isEmpty()) {
            return false;
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(restartArguments);
            if (redirectOutput) {
                processBuilder.inheritIO();
                Process restartedProcess = processBuilder.start();
                restartedProcess.waitFor();
            } else {
                processBuilder.start();
            }
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("等待重新啟動的 JVM 時遭到中斷。");
            exception.printStackTrace();
            return false;
        } catch (IOException exception) {
            System.err.println("重新啟動 JVM 失敗。");
            exception.printStackTrace();
            return false;
        }
    }

    private static void configureWindowsNativeTempDirectory(String operatingSystemName) {
        if (!operatingSystemName.contains("windows")) {
            return;
        }

        String programDataDirectory = System.getenv("ProgramData");
        if (programDataDirectory == null || programDataDirectory.isBlank()) {
            return;
        }

        System.setProperty(
            "java.io.tmpdir",
            new File(programDataDirectory, "libGDX-temp").getAbsolutePath()
        );
    }

    private static List<String> buildRestartArguments(long processId) {
        String javaExecutablePath = resolveJavaExecutablePath();
        if (!new File(javaExecutablePath).isFile()) {
            System.err.println("找不到 Java 執行檔：" + javaExecutablePath);
            return List.of();
        }

        String mainClassName = resolveMainClassName(processId);
        if (mainClassName == null || mainClassName.isBlank()) {
            System.err.println("無法判斷要重新啟動的 main class。");
            return List.of();
        }

        List<String> restartArguments = new ArrayList<>();
        restartArguments.add(javaExecutablePath);
        restartArguments.add("-XstartOnFirstThread");
        restartArguments.add("-D" + JVM_RESTARTED_ARGUMENT + "=true");
        restartArguments.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        restartArguments.add("-cp");
        restartArguments.add(System.getProperty("java.class.path"));
        restartArguments.add(mainClassName);
        return restartArguments;
    }

    private static String resolveJavaExecutablePath() {
        String executableName = System.getProperty("os.name", "").toLowerCase().contains("windows")
            ? "java.exe"
            : "java";
        return new File(new File(System.getProperty("java.home"), "bin"), executableName)
            .getAbsolutePath();
    }

    private static String resolveMainClassName(long processId) {
        String mainClassName = System.getenv("JAVA_MAIN_CLASS_" + processId);
        if (mainClassName != null && !mainClassName.isBlank()) {
            return mainClassName;
        }

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length == 0) {
            return null;
        }
        return stackTrace[stackTrace.length - 1].getClassName();
    }
}
