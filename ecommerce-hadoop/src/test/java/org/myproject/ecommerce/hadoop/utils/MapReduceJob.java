package org.myproject.ecommerce.hadoop.utils;

import com.mongodb.MongoClientURI;
import com.mongodb.hadoop.MongoInputFormat;
import com.mongodb.hadoop.MongoOutputFormat;
import com.mongodb.hadoop.util.MongoTool;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.OutputCommitter;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;

import static com.mongodb.hadoop.util.MongoConfigUtil.INPUT_URI;
import static com.mongodb.hadoop.util.MongoConfigUtil.JOB_INPUT_FORMAT;
import static com.mongodb.hadoop.util.MongoConfigUtil.JOB_OUTPUT_FORMAT;
import static com.mongodb.hadoop.util.MongoConfigUtil.OUTPUT_URI;
import static java.lang.String.format;
import static org.myproject.ecommerce.hadoop.BaseHadoopTest.HADOOP_HOME;
import static org.myproject.ecommerce.hadoop.BaseHadoopTest.HADOOP_VERSION;
import static org.myproject.ecommerce.hadoop.BaseHadoopTest.PROJECT_HOME;
import static org.myproject.ecommerce.hadoop.BaseHadoopTest.isHadoopV1;

public class MapReduceJob {
    private static final Logger LOG = LoggerFactory.getLogger(MapReduceJob.class);

    private Map<String, String> params = new LinkedHashMap<String, String>();
    private final String className;
    private final List<String> inputUris = new ArrayList<String>();
    private MongoClientURI outputUri;
    private File jarPath;
    private Class<? extends InputFormat> inputFormat;
    private Class<? extends OutputFormat> outputFormat;
    private Class<? extends org.apache.hadoop.mapred.InputFormat> mapredInputFormat;
    private Class<? extends org.apache.hadoop.mapred.OutputFormat> mapredOutputFormat;
    private Class<? extends OutputCommitter> outputCommitter;

    private static final int MAX_MINUTES_SUBPROCESS_RUN = 20;

    public MapReduceJob(final String className) {
        this.className = className;
    }

    public MapReduceJob param(final String key, final String value) {
        params.put(key, value);
        return this;
    }

    public MapReduceJob inputUris(final MongoClientURI... inputUris) {
        for (MongoClientURI inputUri : inputUris) {
            this.inputUris.add(inputUri.getURI());
        }
        return this;
    }

    public MapReduceJob outputUri(final MongoClientURI uri) {
        this.outputUri = uri;
        return this;
    }

    public MapReduceJob inputUris(final URI... inputUris) {
        for (URI inputUri : inputUris) {
            this.inputUris.add(inputUri.toString());
        }
        return this;
    }

    public MapReduceJob outputCommitter(
            final Class<? extends OutputCommitter> outputCommitter) {
        this.outputCommitter = outputCommitter;
        return this;
    }

    public MapReduceJob jar(final File path) {
        jarPath = path;
        return this;
    }

    public void execute(final boolean inVM) {
        try {
            copyJars();
            if (inVM) {
                executeInVM();
            } else {
                executeExternal();
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void executeExternal() throws IOException, TimeoutException, InterruptedException {
        LOG.info("executeExternal");

        CommandLine cmdLine = new CommandLine(new File(HADOOP_HOME, "bin/hadoop").getCanonicalPath());
        cmdLine.addArgument("jar");
        cmdLine.addArgument(jarPath.getAbsolutePath());
        cmdLine.addArgument(className);
        for (Pair<String, String> entry : processSettings()) {
            LOG.info("format(key): " + entry.getKey());
            LOG.info("format(value): " + entry.getValue());
            LOG.info("formatted as: " + format("-D%s=%s", entry.getKey(), entry.getValue()));
            cmdLine.addArgument(format("-D%s=%s", entry.getKey(), entry.getValue()));
        }

        Map<String, String> env = new TreeMap<String, String>(System.getenv());
        if (HADOOP_VERSION.startsWith("cdh")) {
            env.put("MAPRED_DIR", "share/hadoop/mapreduce2");
        }

        LOG.info("Environment variables: " + env);

        StringBuilder output = new StringBuilder();
        Arrays.stream(cmdLine.toStrings())
              .forEach(s -> {
                  if (output.length() != 0) {
                      output.append("\t");
                  } else {
                      output.append("\n");
                  }
                  output.append(s);
                  output.append(" \\");
                  output.append("\n");
              });

        LOG.info("Executing hadoop job");
        LOG.info(output.toString());
        LOG.info("cmdLine - " + cmdLine.toString());

        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
        DefaultExecutor executor = new DefaultExecutor();
        executor.setExitValue(1);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(60000 * MAX_MINUTES_SUBPROCESS_RUN);
        executor.setWatchdog(watchdog);
        executor.execute(cmdLine, env, resultHandler);
        resultHandler.waitFor();
        LOG.info("executeExternal return: " + resultHandler.getExitValue());
    }

    @SuppressWarnings("unchecked")
    public void executeInVM() throws Exception {
        LOG.info("executeInVM");

        List<String> cmd = new ArrayList<String>();
        for (Pair<String, String> entry : processSettings()) {
            cmd.add(format("-D%s=%s", entry.getKey(), entry.getValue()));
        }
        Map<String, String> env = new TreeMap<String, String>(System.getenv());
        if (HADOOP_VERSION.startsWith("cdh")) {
            env.put("MAPRED_DIR", "share/hadoop/mapreduce2");
            System.setProperty("MAPRED_DIR", "share/hadoop/mapreduce2");
        }

        LOG.info("Executing hadoop job");

        Class<? extends MongoTool> jobClass = (Class<? extends MongoTool>) Class.forName(className);
        Configuration conf = new Configuration();
        MongoTool app = (MongoTool) jobClass.getConstructor(new Class[]{Configuration.class})
                .newInstance(conf);

        ToolRunner.run(conf, app, cmd.toArray(new String[cmd.size()]));
    }

    private List<Pair<String, String>> processSettings() {
        List<Pair<String, String>> entries = new ArrayList<Pair<String, String>>();
        for (Entry<String, String> entry : params.entrySet()) {
            LOG.info(entry.getKey() + " = " + entry.getValue());
            entries.add(new Pair<String, String>(entry.getKey(), entry.getValue()));
        }

        StringBuilder inputUri = new StringBuilder();
        if (!inputUris.isEmpty()) {
            for (String uri : inputUris) {
                if (inputUri.length() != 0) {
                    inputUri.append(",");
                }
                inputUri.append(uri);
            }
            entries.add(new Pair<String, String>(INPUT_URI, inputUri.toString()));
        }

        if (outputUri != null) {
            entries.add(
                    new Pair<String, String>(OUTPUT_URI, outputUri.toString()));
        }
        if (inputFormat != null) {
            entries.add(new Pair<String, String>(JOB_INPUT_FORMAT, inputFormat.getName()));
        } else if (mapredInputFormat != null) {
            entries.add(new Pair<String, String>(JOB_INPUT_FORMAT, mapredInputFormat.getName()));
        } else {
            String name = isHadoopV1()
                    ? com.mongodb.hadoop.mapred.MongoInputFormat.class.getName()
                    : MongoInputFormat.class.getName();
            entries.add(new Pair<String, String>(JOB_INPUT_FORMAT, name));
            LOG.info(format("No input format defined.  Defaulting to '%s'", name));
        }

        if (outputFormat != null) {
            LOG.info("Adding output format '%s'", outputFormat.getName());
            entries.add(new Pair<String, String>(JOB_OUTPUT_FORMAT, outputFormat.getName()));
        } else if (mapredOutputFormat != null) {
            LOG.info("Adding output format '%s'", mapredOutputFormat.getName());
            entries.add(new Pair<String, String>(JOB_OUTPUT_FORMAT, mapredOutputFormat.getName()));
        } else {
            String name = isHadoopV1()
                    ? com.mongodb.hadoop.mapred.MongoOutputFormat.class.getName()
                    : MongoOutputFormat.class.getName();
            entries.add(new Pair<String, String>(JOB_OUTPUT_FORMAT, name));
            LOG.info(format("No output format defined.  Defaulting to '%s'", name));
        }

        if (outputCommitter != null) {
            entries.add(
                    new Pair<String, String>(
                            "mapred.output.committer.class", outputCommitter.getName()));
        }

        return entries;
    }

    private void copyJars() {
        String hadoopLib = format(isHadoopV1() ? HADOOP_HOME + "/lib" : HADOOP_HOME + "/share/hadoop/common");
        try {
            File hadoopCommonLib = new File(PROJECT_HOME, "library_3rdparty/hadoop_common_lib");
            Arrays.stream(hadoopCommonLib.listFiles((d, name) -> name.endsWith(".jar")))
                  .forEach(f -> {
                      try {
                          FileUtils.copyFile(f, new File(hadoopLib, f.getName()));
                      } catch (IOException e) {
                          e.printStackTrace();
                      }
                  });
//            FileUtils.copyFile(coreJar, new File(hadoopLib, "mongo-hadoop-core.jar"));
            File ecommerceCore = new File(PROJECT_HOME, "ecommerce-core/target")
                    .listFiles(new HadoopVersionFilter())[0];
            FileUtils.copyFile(ecommerceCore, new File(hadoopLib, ecommerceCore.getName()));
            File ecommerceHVDFClient = new File(PROJECT_HOME, "ecommerce-hvdf-client/target")
                    .listFiles(new HadoopVersionFilter())[0];
            FileUtils.copyFile(ecommerceHVDFClient, new File(hadoopLib, ecommerceHVDFClient.getName()));
            File ecommerceHadoop = new File(PROJECT_HOME, "ecommerce-hadoop/target")
                    .listFiles(new HadoopVersionFilter())[0];
            FileUtils.copyFile(ecommerceHadoop, new File(hadoopLib, ecommerceHadoop.getName()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MapReduceJob inputFormat(final Class<? extends InputFormat> inputFormat) {
        this.inputFormat = inputFormat;
        return this;
    }

    public MapReduceJob outputFormat(final Class<? extends OutputFormat> outputFormat) {
        this.outputFormat = outputFormat;
        return this;
    }

    public MapReduceJob mapredInputFormat(final Class<? extends org.apache.hadoop.mapred.InputFormat> inputFormat) {
        this.mapredInputFormat = inputFormat;
        return this;
    }

    public MapReduceJob mapredOutputFormat(final Class<? extends org.apache.hadoop.mapred.OutputFormat> outputFormat) {
        this.mapredOutputFormat = outputFormat;
        return this;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public String getClassName() {
        return className;
    }

    public List<String> getInputUris() {
        return inputUris;
    }

    public MongoClientURI getOutputUri() {
        return outputUri;
    }

    public File getJarPath() {
        return jarPath;
    }

    public Class<? extends InputFormat> getInputFormat() {
        return inputFormat;
    }

    public Class<? extends OutputFormat> getOutputFormat() {
        return outputFormat;
    }

    public Class<? extends org.apache.hadoop.mapred.InputFormat> getMapredInputFormat() {
        return mapredInputFormat;
    }

    public Class<? extends org.apache.hadoop.mapred.OutputFormat> getMapredOutputFormat() {
        return mapredOutputFormat;
    }

    public Class<? extends OutputCommitter> getOutputCommitter() {
        return outputCommitter;
    }

    @Override
    public String toString() {
        return "MapReduceJob{" +
                "params=" + params +
                ", className='" + className + '\'' +
                ", inputUris=" + inputUris +
                ", outputUri=" + outputUri +
                ", jarPath=" + jarPath +
                ", inputFormat=" + inputFormat +
                ", outputFormat=" + outputFormat +
                ", mapredInputFormat=" + mapredInputFormat +
                ", mapredOutputFormat=" + mapredOutputFormat +
                ", outputCommitter=" + outputCommitter +
                '}';
    }

    private static final class Pair<T, U> {
        private T key;
        private U value;

        private Pair(final T key, final U value) {
            this.key = key;
            this.value = value;
        }

        public T getKey() {
            return key;
        }

        public U getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.format("Pair{key=%s, value=%s}", key, value);
        }
    }

}
