package etl.cmd.test;

import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;

//log4j2
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.PairFunction;
import org.junit.Test;

import bdap.util.HdfsUtil;
import etl.cmd.LoadDataCmd;
import etl.util.DBType;
import etl.util.DBUtil;
import scala.Tuple2;

public class TestLoadDatabaseCmd extends TestETLCmd {
	public static final Logger logger = LogManager.getLogger(TestLoadDatabaseCmd.class);
	public static final String testCmdClass="etl.cmd.LoadDataCmd";
	
	public String getResourceSubFolder(){
		return "loadcsv/";
	}
	
	private void loadDynSchemaFun() throws Exception{

		String staticCfgName = "loadcsvds1.properties";
		String wfid="wfid1";
		String prefix = "sgsiwf";
		String localSchemaFileName = "test1_schemas.txt";
		String csvFileName = "MyCore_.csv";

		String inputFolder = "/test/loadcsv/input/";
		String schemaFolder="/test/loadcsv/schema/";
		
		//generate all the data files
		getFs().delete(new Path(inputFolder), true);
		getFs().delete(new Path(schemaFolder), true);
		//
		getFs().mkdirs(new Path(inputFolder));
		getFs().mkdirs(new Path(schemaFolder));
		//copy schema file
		getFs().copyFromLocalFile(new Path(getLocalFolder() + localSchemaFileName), new Path(schemaFolder + localSchemaFileName));
		//copy csv file
		getFs().copyFromLocalFile(new Path(getLocalFolder() + csvFileName), new Path(inputFolder + csvFileName));//csv file must be csvfolder/wfid/tableName
		//run cmd
		LoadDataCmd cmd = new LoadDataCmd("wf1", wfid, this.getResourceSubFolder() + staticCfgName, getDefaultFS(), null);
		
		DBUtil.executeSqls(cmd.getCreateSqls(), cmd.getPc());
		cmd.sgProcess();
		List<String> sqls = cmd.getSgCopySql();
		DBUtil.executeSqls(cmd.getDropSqls(), cmd.getPc());
		String hdfsroot = cmd.getPc().getString("hdfs.webhdfs.root");
		String dbuser = cmd.getPc().getString("db.user");
		//assertion
		logger.info(sqls);
		String sqlVertica = "copy sgsiwf.MyCore_(endTime enclosed by '\"',duration enclosed by '\"',SubNetwork enclosed by '\"',"
				+ "ManagedElement enclosed by '\"',Machine enclosed by '\"',MyCore enclosed by '\"',UUID enclosed by '\"',"
				+ "VS_avePerCoreCpuUsage enclosed by '\"',VS_peakPerCoreCpuUsage enclosed by '\"') "
				+ String.format("SOURCE Hdfs(url='%s/test/loadcsv/input/MyCore_.csv*',username='%s') delimiter ',';", hdfsroot, dbuser);
		String sqlHive = String.format("load data inpath '%s/test/loadcsv/input/MyCore_.csv' into table sgsiwf.MyCore_", hdfsroot);
		logger.info("sqlVertica:" + sqlVertica);
		if (cmd.getDbtype()==DBType.HIVE){
			assertTrue(sqls.contains(sqlHive));
		}else{
			assertTrue(sqls.contains(sqlVertica));
		}
	}

	@Test
	public void testLoadDynSchema() throws Exception{
		if (getDefaultFS().contains("127.0.0.1")){
			loadDynSchemaFun();
		}else{
			UserGroupInformation ugi = UserGroupInformation.createProxyUser("dbadmin", UserGroupInformation.getLoginUser());
			ugi.doAs(new PrivilegedExceptionAction<Void>() {
				public Void run() throws Exception {
					loadDynSchemaFun();
					return null;
				}
			});
		}
	}
	
	private void loadDynSchemaExpFun() throws Exception{
		String staticCfgName = "loadcsvdsexp.properties";
		String wfid="wfid1";
		String prefix = "sgsiwf";
		String localSchemaFileName = "multipleTableSchemas.txt";
		String[] csvFileNames = new String[]{"MyCore_.csv", "MyCore1_.csv"};

		String inputFolder = "/test/loadcsv/input/";
		String schemaFolder="/test/loadcsv/schema/";
		
		//generate all the data files
		getFs().delete(new Path(inputFolder), true);
		getFs().delete(new Path(schemaFolder), true);
		//
		getFs().mkdirs(new Path(inputFolder));
		getFs().mkdirs(new Path(schemaFolder));
		//copy schema file
		getFs().copyFromLocalFile(new Path(getLocalFolder() + localSchemaFileName), new Path(schemaFolder + localSchemaFileName));
		//copy csv file
		for (String csvFileName: csvFileNames){
			getFs().copyFromLocalFile(new Path(getLocalFolder() + csvFileName), new Path(inputFolder + csvFileName));//csv file must be csvfolder/wfid/tableName
		}
		//run cmd
		LoadDataCmd cmd = new LoadDataCmd("wf1", wfid, this.getResourceSubFolder() + staticCfgName, getDefaultFS(), null);
		DBUtil.executeSqls(cmd.getCreateSqls(), cmd.getPc());
		cmd.sgProcess();
		List<String> sqls = cmd.getSgCopySql();
		DBUtil.executeSqls(cmd.getDropSqls(), cmd.getPc());

		String hdfsroot = cmd.getPc().getString("hdfs.webhdfs.root");
		String dbuser = cmd.getPc().getString("db.user");
		//assertion
		logger.info(sqls);
		String sqlVertica1 = "copy sgsiwf.MyCore_(endTime enclosed by '\"',duration enclosed by '\"',SubNetwork enclosed by '\"',"
				+ "ManagedElement enclosed by '\"',Machine enclosed by '\"',MyCore enclosed by '\"',UUID enclosed by '\"',"
				+ "VS_avePerCoreCpuUsage enclosed by '\"',VS_peakPerCoreCpuUsage enclosed by '\"') "
				+ String.format("SOURCE Hdfs(url='%s/test/loadcsv/input/MyCore_.csv*',username='%s') delimiter ',';", hdfsroot, dbuser);
		String sqlVertica2 = "copy sgsiwf.MyCore1_(endTime enclosed by '\"',duration enclosed by '\"',SubNetwork enclosed by '\"',"
				+ "ManagedElement enclosed by '\"',Machine enclosed by '\"',MyCore enclosed by '\"',UUID enclosed by '\"',"
				+ "VS_avePerCoreCpuUsage enclosed by '\"',VS_peakPerCoreCpuUsage enclosed by '\"') "
				+ String.format("SOURCE Hdfs(url='%s/test/loadcsv/input/MyCore1_.csv*',username='%s') delimiter ',';", hdfsroot, dbuser);
		String sqlHive1 = String.format("load data inpath '%s/test/loadcsv/input/MyCore_.csv' into table sgsiwf.MyCore_", hdfsroot);
		String sqlHive2 = String.format("load data inpath '%s/test/loadcsv/input/MyCore1_.csv' into table sgsiwf.MyCore1_", hdfsroot);
		
		if (DBType.HIVE == cmd.getDbtype()){
			assertTrue(sqls.contains(sqlHive1));
			assertTrue(sqls.contains(sqlHive2));
		}else{
			logger.info(sqlVertica1);
			assertTrue(sqls.contains(sqlVertica1));
			logger.info(sqlVertica2);
			assertTrue(sqls.contains(sqlVertica2));
		}
	}

	@Test
	public void testLoadDynSchemaExp() throws Exception{
		if (getDefaultFS().contains("127.0.0.1")){
			loadDynSchemaExpFun();
		}else{
			UserGroupInformation ugi = UserGroupInformation.createProxyUser("dbadmin", UserGroupInformation.getLoginUser());
			ugi.doAs(new PrivilegedExceptionAction<Void>() {
				public Void run() throws Exception {
					loadDynSchemaExpFun();
					return null;
				}
			});
		}
	}
	
	@Test
	public void testLoadDataMR() throws Exception{
		String staticCfgName = "loadcsvmr.properties";
		String remoteCsvInputFolder = "/test/loadcsv/input/";
		String remoteCsvOutputFolder = "/test/loadcsv/output/";
		String schemaFolder="/test/loadcsv/schema/";
		String localSchemaFileName = "multipleTableSchemas.txt";
		String[] csvFileNames = new String[]{"MyCore_.csv", "MyCore1_.csv"};
		
		//generate all the data files
		getFs().delete(new Path(remoteCsvInputFolder), true);
		getFs().delete(new Path(remoteCsvOutputFolder), true);
		getFs().delete(new Path(schemaFolder), true);
		//
		getFs().mkdirs(new Path(remoteCsvInputFolder));
		getFs().mkdirs(new Path(remoteCsvOutputFolder));
		getFs().mkdirs(new Path(schemaFolder));
		//copy schema file
		getFs().copyFromLocalFile(new Path(getLocalFolder() + localSchemaFileName), new Path(schemaFolder + localSchemaFileName));
		//copy csv file
		for (String csvFileName: csvFileNames){
			getFs().copyFromLocalFile(new Path(getLocalFolder() + csvFileName), new Path(remoteCsvInputFolder + csvFileName));//csv file must be csvfolder/wfid/tableName
		}
		
		LoadDataCmd cmd = new LoadDataCmd("wf1", "wfid1", this.getResourceSubFolder() + staticCfgName, getDefaultFS(), null);
		
		DBUtil.executeSqls(cmd.getCreateSqls(), cmd.getPc());
		
		List<String> output = super.mrTest(remoteCsvInputFolder, remoteCsvOutputFolder, staticCfgName, csvFileNames, testCmdClass, true);
		logger.info("Output is:"+output);
		
		List<String> fl = HdfsUtil.listDfsFile(getFs(), "/test/loadcsv/output");
		//assert
		logger.info(fl);
	}
	
	@Test
	public void testSpark1() throws Exception{
		String staticCfgName = "loadcsv.spark.properties";
		String wfid="wfid1";
		String localSchemaFileName = "test1_schemas.txt";
		String csvFileName = "MyCore_.csv";
		String tableName = "MyCore_";

		String inputFolder = "/test/loadcsv/input/";
		String schemaFolder="/test/loadcsv/schema/";
		
		//copy schema file
		getFs().delete(new Path(inputFolder), true);
		getFs().mkdirs(new Path(inputFolder));
		getFs().copyFromLocalFile(false, true, new Path(getLocalFolder() + localSchemaFileName), new Path(schemaFolder + localSchemaFileName));
		//run cmd
		LoadDataCmd cmd = new LoadDataCmd("wf1", wfid, this.getResourceSubFolder() + staticCfgName, getDefaultFS(), null);
		SparkConf conf = new SparkConf().setAppName(wfid).setMaster("local[3]");
		JavaSparkContext jsc = new JavaSparkContext(conf);
		JavaRDD<String> lines = jsc.textFile("file:///" + getLocalFolder() + csvFileName);
		JavaPairRDD<String, String> input = lines.mapToPair(new PairFunction<String,String,String>(){
			@Override
			public Tuple2<String, String> call(String t) throws Exception {
				return new Tuple2<String, String>(tableName, t);
			}
		});
		cmd.sparkProcessKeyValue(input, jsc);
		//check hdfs
		List<String> files = HdfsUtil.listDfsFile(super.getFs(), inputFolder);
		logger.info(files);
		assertTrue(files.contains(csvFileName));
	}
}
