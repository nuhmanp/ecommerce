package org.myproject.ecommerce.hadoop;

import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.hadoop.util.MongoClientURIBuilder;
import org.myproject.ecommerce.hadoop.utils.HadoopVersionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

public abstract class BaseHadoopTest {
    private static final Logger LOG = LoggerFactory.getLogger(BaseHadoopTest.class);

    public static final String HADOOP_HOME;
    public static final String PROJECT_VERSION = loadProperty("project"
            + ".version", "1.0-SNAPSHOT");
    public static final String HADOOP_VERSION = loadProperty("hadoop.version", "2.7.2");

    public static final File PROJECT_HOME;
    public static final String HADOOP_BINARIES;
//    public static final String EXAMPLE_DATA_HOME;

    private static final boolean TEST_IN_VM = Boolean.valueOf(System.getProperty("mongo.hadoop.testInVM", "false"));

    private static final String MONGO_IMPORT = System.getProperty("mongodb_bin_dir") == null
            ? "/usr/local/bin/mongoimport"
            : System.getProperty("mongodb_bin_dir") + "/mongoimport";

    private MongoClient client;

    static {
        try {
            File current = new File(".").getCanonicalFile();
            while (!new File(current, "ecommerce-core").exists() && current.getParentFile().exists()) {
                current = current.getParentFile();
            }
            PROJECT_HOME = current;
//
//            final File gradleProps = new File(PROJECT_HOME, ".gradle.properties");
//            if (gradleProps.exists()) {
//                System.getProperties().load(new FileInputStream(gradleProps));
//            }
            HADOOP_BINARIES = new File(PROJECT_HOME, "hadoop-binaries/").getCanonicalPath();
//            EXAMPLE_DATA_HOME = new File(HADOOP_BINARIES, "examples/data").getCanonicalPath();

            HADOOP_HOME = new File(HADOOP_BINARIES, format("hadoop-%s", HADOOP_VERSION)).getCanonicalPath();
            LOG.info("HADOOP_HOME = " + HADOOP_HOME);

        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public int[] getServerVersion(final MongoClientURI uri) {
        List versionArray = (List) getClient(uri).getDB("admin")
                .command("buildinfo").get("versionArray");
        int[] versionDigits = new int[versionArray.size()];
        for (int i = 0; i < versionArray.size(); ++i) {
            versionDigits[i] = (Integer) versionArray.get(i);
        }
        return versionDigits;
    }

    public boolean isSampleOperatorSupported(final MongoClientURI uri) {
        int[] serverVersion = getServerVersion(uri);
        return (serverVersion[0] > 3
                || (serverVersion[0] == 3 && serverVersion[1] >= 2));
    }

    protected static String loadProperty(final String name, final String defaultValue) {
        String property = System.getProperty(name, System.getenv(name.toUpperCase()));
        if (property == null) {
            property = defaultValue;
        }
        return property;
    }


    protected static DBObject dbObject(final Object... values) {
        final BasicDBObject object = new BasicDBObject();
        for (int i = 0; i < values.length; i += 2) {
            object.append(values[i].toString(), values[i + 1]);
        }
        return object;
    }

    public static MongoClientURIBuilder authCheck(final MongoClientURIBuilder builder) {
        if (isAuthEnabled()) {
            builder.auth("bob", "pwd123");
        }

        return builder;
    }

    protected static File findProjectJar(final File root) {
        return findProjectJar(root, false);
    }

    protected static File findProjectJar(final File root, final boolean findTestJar) {
        try {
//            File current = new File(".").getCanonicalFile();
//            File core = new File(current, "core");
//            while (!core.exists() && current.getParentFile().exists()) {
//                current = current.getParentFile();
//                core = new File(current, "core");
//            }

            final File file = new File(root, "target").getCanonicalFile();
            final File[] files = file.listFiles(new HadoopVersionFilter());
            if (files.length == 0) {
                throw new RuntimeException(format("Can't find jar.  project version = %s, path = %s, findTestJar = %s",
                        PROJECT_VERSION, file, findTestJar));
            }
            return files[0];
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static boolean isHadoopV1() {
        return BaseHadoopTest.HADOOP_VERSION.startsWith("1.");
    }

    public MongoClient getClient(final MongoClientURI uri) {
        if (client == null) {
            try {
                client = new MongoClient(uri);
            } catch (final Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        return client;
    }

    protected List<DBObject> toList(final DBCursor cursor) {
        final List<DBObject> list = new ArrayList<DBObject>();
        while (cursor.hasNext()) {
            list.add(cursor.next());
        }

        return list;
    }

    protected static boolean isAuthEnabled() {
        LOG.info("authEnabled: " + System.getProperty("authEnabled", "false"));
        return Boolean.valueOf(System.getProperty("authEnabled", "false"))
                || "auth".equals(System.getProperty("mongodb_option"));
    }

    protected boolean isSharded(final MongoClientURI uri) {
        final CommandResult isMasterResult = runIsMaster(uri);
        final Object msg = isMasterResult.get("msg");
        LOG.info("msg of isMasterResult(type): " + msg.getClass().getName());
        LOG.info("msg of isMasterResult(value): " + msg);
        return msg != null && msg.equals("isdbgrid");
    }

    protected CommandResult runIsMaster(final MongoClientURI uri) {
        // Check to see if this is a replica set... if not, get out of here.
        return getClient(uri).getDB("admin").command(new BasicDBObject("ismaster", 1));
    }

    public static boolean isRunTestInVm() {
        return TEST_IN_VM;
    }
}
