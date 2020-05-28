/*
Copyright 2017 Mattia Atzeni, Maurizio Atzori

This file is part of CodeOntology.

CodeOntology is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

CodeOntology is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with CodeOntology.  If not, see <http://www.gnu.org/licenses/>
*/

package org.codeontology;

import com.martiansoftware.jsap.*;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;

public class CodeOntologyArguments {

    public static final String INPUT_LONG = "input";
    public static final char INPUT_SHORT = 'i';

    public static final String OUTPUT_LONG = "output";
    public static final char OUTPUT_SHORT = 'o';

    public static final String CLASSPATH_LONG = "classpath";

    public static final String DO_NOT_DOWNLOAD_LONG = "do-not-download";

    public static final String VERBOSE_LONG = "verbose";

    public static final String STACKTRACE_LONG = "stacktrace";
    public static final char STACKTRACE_SHORT = 't';

    public static final String HELP_LONG = "help";
    public static final char HELP_SHORT = 'h';

    public static final String SHUTDOWN_LONG = "shutdown";

    public static final String JAR_INPUT_LONG = "jar";

    public static final String DEPENDENCIES_LONG = "dependencies";
    public static final char DEPENDENCIES_SHORT = 'd';

    public static final String DO_NOT_EXTRACT_LONG = "do-not-extract";

    public static final String COMPLIANCE_LEVEL_LONG = "compliance-level";
    public static final char COMPLIANCE_LEVEL_SHORT = 'c';

    public static final String VERSION_LONG = "version";
    public static final char VERSION_SHORT = 'v';

    public static final String COMMIT_ID_LONG = "commit-id";

    public static final String IS_VERSION_UPDATE_LONG = "is-version-update";

    public static final String PROJECT_ID_LONG = "project-id";
    public static final char PROJECT_ID_SHORT = 'p';

    public static final String IGNORE_DIR_LONG = "ignore-dir";

    public static final String IGNORE_File_LONG = "ignore-file";

    public static final String DO_NOT_LOAD_MAVEN = "do-not-load-maven";

    private JSAP jsap;
    private JSAPResult result;

    public CodeOntologyArguments(String[] args) throws JSAPException {
        defineArgs();
        result = parseArgs(args);
    }

    private void defineArgs() throws JSAPException {
        this.jsap = new JSAP();
        FlaggedOption option;
        Switch flag;

        option = new FlaggedOption(INPUT_LONG);
        option.setShortFlag(INPUT_SHORT);
        option.setLongFlag(INPUT_LONG);
        option.setStringParser(JSAP.STRING_PARSER);
        option.setHelp("Path to source files.");
        jsap.registerParameter(option);

//        option = new FlaggedOption(PROJECT_ID_LONG);
//        option.setLongFlag(PROJECT_ID_LONG);
//        option.setShortFlag(PROJECT_ID_SHORT);
//        option.setStringParser(JSAP.STRING_PARSER);
//        option.setRequired(true);
//        option.setHelp("project name to identify the project and its version.");
//        jsap.registerParameter(option);

        option = new FlaggedOption(OUTPUT_LONG);
        option.setShortFlag(OUTPUT_SHORT);
        option.setLongFlag(OUTPUT_LONG);
        option.setStringParser(JSAP.STRING_PARSER);
        option.setRequired(false);
        option.setDefault(getDefaultOutput());
        option.setHelp("Output file name.");
        jsap.registerParameter(option);

        option = new FlaggedOption(JAR_INPUT_LONG);
        option.setLongFlag(JAR_INPUT_LONG);
        option.setStringParser(JSAP.STRING_PARSER);
        option.setRequired(false);
        option.setHelp("Path to a jar input file");
        jsap.registerParameter(option);

        option = new FlaggedOption(CLASSPATH_LONG);
        option.setLongFlag(CLASSPATH_LONG);
        option.setStringParser(JSAP.STRING_PARSER);
        option.setRequired(false);
        option.setHelp("Specifies a list of directories and JAR files separated by colons (:) to search for class files.");
        jsap.registerParameter(option);

        flag = new Switch(DO_NOT_DOWNLOAD_LONG);
        flag.setLongFlag(DO_NOT_DOWNLOAD_LONG);
        flag.setDefault("false");
        flag.setHelp("Do not download dependencies.");
        jsap.registerParameter(flag);

        flag = new Switch(VERBOSE_LONG);
        flag.setLongFlag(VERBOSE_LONG);
        flag.setDefault("false");
        flag.setHelp("Verbosely lists all files processed.");
        jsap.registerParameter(flag);

        flag = new Switch(STACKTRACE_LONG);
        flag.setLongFlag(STACKTRACE_LONG);
        flag.setShortFlag(STACKTRACE_SHORT);
        flag.setDefault("false");
        flag.setHelp("Print stack trace for exceptions.");
        jsap.registerParameter(flag);

        flag = new Switch(HELP_LONG);
        flag.setLongFlag(HELP_LONG);
        flag.setShortFlag(HELP_SHORT);
        flag.setDefault("false");
        flag.setHelp("Print this help message.");
        jsap.registerParameter(flag);

        flag = new Switch(DEPENDENCIES_LONG);
        flag.setLongFlag(DEPENDENCIES_LONG);
        flag.setShortFlag(DEPENDENCIES_SHORT);
        flag.setDefault("false");
        flag.setHelp("Explore jar files in classpath");
        jsap.registerParameter(flag);

        flag = new Switch(SHUTDOWN_LONG);
        flag.setLongFlag(SHUTDOWN_LONG);
        flag.setDefault("false");
        flag.setHelp("Shutdown after completing extraction");
        jsap.registerParameter(flag);

        flag = new Switch(DO_NOT_EXTRACT_LONG);
        flag.setLongFlag(DO_NOT_EXTRACT_LONG);
        flag.setDefault("false");
        flag.setHelp("Do not extract triples, just download dependencies");
        jsap.registerParameter(flag);

        option = new FlaggedOption(COMPLIANCE_LEVEL_LONG);
        option.setLongFlag(COMPLIANCE_LEVEL_LONG);
        option.setShortFlag(COMPLIANCE_LEVEL_SHORT);
        option.setStringParser(JSAP.INTEGER_PARSER);
        option.setRequired(false);
        option.setDefault("7");
        option.setHelp("set the Java version compliance level.");
        jsap.registerParameter(option);

        option = new FlaggedOption(VERSION_LONG);
        option.setLongFlag(VERSION_LONG);
        option.setShortFlag(VERSION_SHORT);
        option.setStringParser(JSAP.STRING_PARSER);
        option.setRequired(false);
        option.setHelp("input project version. eg. 1.0");
        jsap.registerParameter(option);

        option = new FlaggedOption(COMMIT_ID_LONG);
        option.setLongFlag(COMMIT_ID_LONG);
        option.setStringParser(JSAP.STRING_PARSER);
        option.setRequired(true);
        option.setHelp("commit id of input project.");
        jsap.registerParameter(option);

        flag = new Switch(IS_VERSION_UPDATE_LONG);
        flag.setLongFlag(IS_VERSION_UPDATE_LONG);
        flag.setDefault("false");
        flag.setHelp("this operation if is version update, not new add.");
        jsap.registerParameter(flag);

        option = new FlaggedOption(PROJECT_ID_LONG);
        option.setShortFlag(PROJECT_ID_SHORT);
        option.setLongFlag(PROJECT_ID_LONG);
        option.setStringParser(JSAP.STRING_PARSER);
        option.setRequired(true);
        option.setHelp("project identifier to connect corresponding databases.");
        jsap.registerParameter(option);

        option = new FlaggedOption(IGNORE_DIR_LONG);
        option.setLongFlag(IGNORE_DIR_LONG);
        option.setStringParser(JSAP.STRING_PARSER);
        option.setHelp("directory patterns that need to ingore, splited by ','.");
        jsap.registerParameter(option);

        option = new FlaggedOption(IGNORE_File_LONG);
        option.setLongFlag(IGNORE_File_LONG);
        option.setStringParser(JSAP.STRING_PARSER);
        option.setHelp("file names that need to ingore, splited by ','.");
        jsap.registerParameter(option);

        flag = new Switch(DO_NOT_LOAD_MAVEN);
        flag.setLongFlag(DO_NOT_LOAD_MAVEN);
        flag.setDefault("false");
        flag.setHelp("skip loading maven dependencies.");
        jsap.registerParameter(flag);
    }

    public JSAPResult parseArgs(String[] args) throws JSAPException {

        defineArgs();

        if (args == null) {
            args = new String[0];
        }

        JSAPResult arguments = jsap.parse(args);

        if (arguments.getBoolean(HELP_LONG)) {
            printHelp();
            System.exit(0);
        }

        if (!arguments.success()) {
            // print out specific error messages describing the problems
            java.util.Iterator<?> errs = arguments.getErrorMessageIterator();
            while (errs.hasNext()) {
                throw new RuntimeException("Error: " + errs.next());
            }

            printHelp();
        }


        return arguments;
    }

    private void printHelp() {
        printUsage();
        System.err.println("Options:");
        System.err.println();
        System.err.println(jsap.getHelp());
    }

    private void printUsage() {
        System.err.println("Usage:");
        System.err.println("codeontology -i <input_folder> -o <output_file>");
        System.err.println();
    }

    public String getInput() {
        return result.getString(INPUT_LONG);
    }

    public String getOutput() {
        return result.getString(OUTPUT_LONG);
    }

    public boolean downloadDependencies() {
        return !result.getBoolean(DO_NOT_DOWNLOAD_LONG);
    }

    public boolean verboseMode() {
        return result.getBoolean(VERBOSE_LONG);
    }

    public boolean stackTraceMode() {
        return result.getBoolean(STACKTRACE_LONG);
    }

    public boolean shutdownFlag() {
        return result.getBoolean(SHUTDOWN_LONG);
    }

    public boolean doNotExtractTriples() {
        return result.getBoolean(DO_NOT_EXTRACT_LONG);
    }

    private String getDefaultOutput() {
        final int MAX = 100;

        int i = 0;

        String defaultName;
        File file;

        do {
            Formatter formatter = new Formatter();
            formatter.format("triples%02d.nt", i);
            defaultName = formatter.toString();
            file = new File(defaultName);
            i++;
        } while (i < MAX && file.exists());

        if (i > MAX) {
            throw new RuntimeException("Specify an output file");
        }

        return defaultName;
    }

    public String getJarInput() {
        return result.getString(JAR_INPUT_LONG);
    }

    public boolean exploreJars() {
        return result.getBoolean(DEPENDENCIES_LONG);
    }

    public String getClasspath() {
        return result.getString(CLASSPATH_LONG);
    }

    public int complianceLevel() {
        return result.getInt(COMPLIANCE_LEVEL_LONG);
    }

    public String getVersion() {
        return result.getString(VERSION_LONG);
    }

    public String getCommitID() {
        return result.getString(COMMIT_ID_LONG);
    }

    public boolean isVersionUpdate(){
        return result.getBoolean(IS_VERSION_UPDATE_LONG);
    }

    public String getProjectID(){
        return result.getString(PROJECT_ID_LONG);
    }

    public List<String> getIgnoreDir(){
        String ignoreDir = result.getString(IGNORE_DIR_LONG);
        if(StringUtils.isEmpty(ignoreDir)){
            return null;
        }
        return Arrays.asList(ignoreDir.split(","));
    }

    public List<String> getIgnoreFile(){
        String ignoreFile = result.getString(IGNORE_File_LONG);
        if(StringUtils.isEmpty(ignoreFile)){
            return null;
        }
        return Arrays.asList(ignoreFile.split(","));
    }

    public boolean doNotLoadMaven() {
        return result.getBoolean(DO_NOT_LOAD_MAVEN);
    }
}