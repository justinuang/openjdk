/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

import jdk.test.lib.Utils;
import jdk.test.lib.BuildHelper;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Platform;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * This is a test utility class for common AppCDS test functionality.
 *
 * Various methods use (String ...) for passing VM options. Note that the order
 * of the VM options are important in certain cases. Many methods take arguments like
 *
 *    (String prefix[], String suffix[], String... opts)
 *
 * Note that the order of the VM options is:
 *
 *    prefix + opts + suffix
 */
public class TestCommon extends CDSTestUtils {
    private static final String JSA_FILE_PREFIX = System.getProperty("user.dir") +
        File.separator + "appcds-";

    private static final SimpleDateFormat timeStampFormat =
        new SimpleDateFormat("HH'h'mm'm'ss's'SSS");

    private static final String timeoutFactor =
        System.getProperty("test.timeout.factor", "1.0");

    private static String currentArchiveName;

    // Call this method to start new archive with new unique name
    public static void startNewArchiveName() {
        deletePriorArchives();
        currentArchiveName = JSA_FILE_PREFIX +
            timeStampFormat.format(new Date()) + ".jsa";
    }

    // Call this method to get current archive name
    public static String getCurrentArchiveName() {
        return currentArchiveName;
    }

    // Attempt to clean old archives to preserve space
    // Archives are large artifacts (20Mb or more), and much larger than
    // most other artifacts created in jtreg testing.
    // Therefore it is a good idea to clean the old archives when they are not needed.
    // In most cases the deletion attempt will succeed; on rare occasion the
    // delete operation will fail since the system or VM process still holds a handle
    // to the file; in such cases the File.delete() operation will silently fail, w/o
    // throwing an exception, thus allowing testing to continue.
    public static void deletePriorArchives() {
        File dir = new File(System.getProperty("user.dir"));
        String files[] = dir.list();
        for (String name : files) {
            if (name.startsWith("appcds-") && name.endsWith(".jsa")) {
                if (!(new File(dir, name)).delete())
                    System.out.println("deletePriorArchives(): delete failed for file " + name);
            }
        }
    }


    // Create AppCDS archive using most common args - convenience method
    // Legacy name preserved for compatibility
    public static OutputAnalyzer dump(String appJar, String appClasses[],
                                               String... suffix) throws Exception {
        return createArchive(appJar, appClasses, suffix);
    }


    // Create AppCDS archive using most common args - convenience method
    public static OutputAnalyzer createArchive(String appJar, String appClasses[],
                                               String... suffix) throws Exception {
        AppCDSOptions opts = (new AppCDSOptions()).setAppJar(appJar)
            .setAppClasses(appClasses);
        opts.addSuffix(suffix);
        return createArchive(opts);
    }

    // If you use -XX:+UseAppCDS or -XX:-UseAppCDS in your JVM command-line, call this method
    // to wrap the arguments. On commercial builds, -XX:+UnlockCommercialFeatures will be
    // prepended to the command-line. See JDK-8193664.
    public static String[] makeCommandLineForAppCDS(String... args) throws Exception {
        if (BuildHelper.isCommercialBuild()) {
            String[] newArgs = new String[args.length + 1];
            newArgs[0] = "-XX:+UnlockCommercialFeatures";
            System.arraycopy(args, 0, newArgs, 1, args.length);
            return newArgs;
        } else {
            return args;
        }
    }

    // Create AppCDS archive using appcds options
    public static OutputAnalyzer createArchive(AppCDSOptions opts)
        throws Exception {

        ArrayList<String> cmd = new ArrayList<String>();
        File classList = makeClassList(opts.appClasses);
        startNewArchiveName();

        for (String p : opts.prefix) cmd.add(p);

        if (opts.appJar != null) {
            cmd.add("-cp");
            cmd.add(opts.appJar);
        } else {
            cmd.add("-cp");
            cmd.add("\"\"");
        }

        cmd.add("-Xshare:dump");
        cmd.add("-Xlog:cds,cds+hashtables");
        cmd.add("-XX:+UseAppCDS");
        cmd.add("-XX:ExtraSharedClassListFile=" + classList.getPath());

        if (opts.archiveName == null)
            opts.archiveName = getCurrentArchiveName();

        cmd.add("-XX:SharedArchiveFile=" + opts.archiveName);

        for (String s : opts.suffix) cmd.add(s);

        String[] cmdLine = cmd.toArray(new String[cmd.size()]);
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true, makeCommandLineForAppCDS(cmdLine));
        return executeAndLog(pb, "dump");
    }


    // Execute JVM using AppCDS archive with specified AppCDSOptions
    public static OutputAnalyzer runWithArchive(AppCDSOptions opts)
        throws Exception {

        ArrayList<String> cmd = new ArrayList<String>();

        for (String p : opts.prefix) cmd.add(p);

        cmd.add("-Xshare:" + opts.xShareMode);
        cmd.add("-XX:+UseAppCDS");
        cmd.add("-showversion");
        cmd.add("-XX:SharedArchiveFile=" + getCurrentArchiveName());
        cmd.add("-Dtest.timeout.factor=" + timeoutFactor);

        if (opts.appJar != null) {
            cmd.add("-cp");
            cmd.add(opts.appJar);
        }

        for (String s : opts.suffix) cmd.add(s);

        String[] cmdLine = cmd.toArray(new String[cmd.size()]);
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true, makeCommandLineForAppCDS(cmdLine));
        return executeAndLog(pb, "exec");
    }


    public static OutputAnalyzer execCommon(String... suffix) throws Exception {
        AppCDSOptions opts = (new AppCDSOptions());
        opts.addSuffix(suffix);
        return runWithArchive(opts);
    }


    public static OutputAnalyzer exec(String appJar, String... suffix) throws Exception {
        AppCDSOptions opts = (new AppCDSOptions()).setAppJar(appJar);
        opts.addSuffix(suffix);
        return runWithArchive(opts);
    }


    public static OutputAnalyzer execAuto(String... suffix) throws Exception {
        AppCDSOptions opts = (new AppCDSOptions());
        opts.addSuffix(suffix).setXShareMode("auto");
        return runWithArchive(opts);
    }

    public static OutputAnalyzer execOff(String... suffix) throws Exception {
        AppCDSOptions opts = (new AppCDSOptions());
        opts.addSuffix(suffix).setXShareMode("off");
        return runWithArchive(opts);
    }

    public static OutputAnalyzer execModule(String prefix[], String upgrademodulepath, String modulepath,
                                            String mid, String... testClassArgs)
        throws Exception {

        AppCDSOptions opts = (new AppCDSOptions());

        opts.addPrefix(prefix);
        if (upgrademodulepath == null) {
            opts.addSuffix("-p", modulepath, "-m", mid);
        } else {
            opts.addSuffix("--upgrade-module-path", upgrademodulepath,
                           "-p", modulepath, "-m", mid);
        }
        opts.addSuffix(testClassArgs);

        return runWithArchive(opts);
    }


    // A common operation: dump, then check results
    public static OutputAnalyzer testDump(String appJar, String appClasses[],
                                          String... suffix) throws Exception {
        OutputAnalyzer output = dump(appJar, appClasses, suffix);
        output.shouldContain("Loading classes to share");
        output.shouldHaveExitValue(0);
        return output;
    }


    /**
     * Simple test -- dump and execute appJar with the given appClasses in classlist.
     */
    public static OutputAnalyzer test(String appJar, String appClasses[], String... args)
        throws Exception {
        testDump(appJar, appClasses);

        OutputAnalyzer output = exec(appJar, args);
        return checkExec(output);
    }


    public static OutputAnalyzer checkExecReturn(OutputAnalyzer output, int ret,
                           boolean checkContain, String... matches) throws Exception {
        try {
            for (String s : matches) {
                if (checkContain) {
                    output.shouldContain(s);
                } else {
                    output.shouldNotContain(s);
                }
            }
            output.shouldHaveExitValue(ret);
        } catch (Exception e) {
            checkCommonExecExceptions(output, e);
        }

        return output;
    }


    // Convenience concatenation utils
    public static String[] list(String ...args) {
        return args;
    }


    public static String[] list(String arg, int count) {
        ArrayList<String> stringList = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            stringList.add(arg);
        }

        String outputArray[] = stringList.toArray(new String[stringList.size()]);
        return outputArray;
    }


    public static String[] concat(String... args) {
        return list(args);
    }


    public static String[] concat(String prefix[], String... extra) {
        ArrayList<String> list = new ArrayList<String>();
        for (String s : prefix) {
            list.add(s);
        }
        for (String s : extra) {
            list.add(s);
        }

        return list.toArray(new String[list.size()]);
    }


    // ===================== Concatenate paths
    public static String concatPaths(String... paths) {
        String prefix = "";
        String s = "";
        for (String p : paths) {
            s += prefix;
            s += p;
            prefix = File.pathSeparator;
        }
        return s;
    }


    public static String getTestJar(String jar) {
        File jarFile = CDSTestUtils.getTestArtifact(jar, true);
        if (!jarFile.isFile()) {
            throw new RuntimeException("Not a regular file: " + jarFile.getPath());
        }
        return jarFile.getPath();
    }


    public static String getTestDir(String d) {
        File dirFile = CDSTestUtils.getTestArtifact(d, true);
        if (!dirFile.isDirectory()) {
            throw new RuntimeException("Not a directory: " + dirFile.getPath());
        }
        return dirFile.getPath();
    }
}
