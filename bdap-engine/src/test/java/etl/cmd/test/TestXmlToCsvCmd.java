package etl.cmd.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.fs.Path;
import org.junit.Test;

import bdap.util.HdfsUtil;
import etl.util.XMLInputFormat;
import scala.Tuple2;

//log4j2
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestXmlToCsvCmd extends TestETLCmd{

	public static final Logger logger = LogManager.getLogger(TestXmlToCsvCmd.class);
	
	private static final String cmdClassName = "etl.cmd.XmlToCsvCmd";

	public String getResourceSubFolder(){
		return "xmltocsv/";
	}
	
	@Test
	public void test1() throws Exception{
		//
		String inputFolder = "/test/xml2csv/input/";
		String outputFolder = "/test/xml2csv/output/";
		String schemaFolder="/test/xml2csv/schema/";
		
		String staticCfgName = "xml2csv1.properties";
		String[] inputFiles = new String[]{"dynschema_test1_data.xml"};
		String localSchemaFile = "dynschema_test1_schemas.txt";
		String remoteSchemaFile = "schemas.txt";
		
		//schema
		getFs().delete(new Path(schemaFolder), true);
		getFs().mkdirs(new Path(schemaFolder));
		getFs().copyFromLocalFile(new Path(getLocalFolder() + localSchemaFile), new Path(schemaFolder + remoteSchemaFile));
		
		//run cmd
		List<Tuple2<String, String[]>> rfifs = new ArrayList<Tuple2<String, String[]>>();
		rfifs.add(new Tuple2<String, String[]>(inputFolder, inputFiles));
		getConf().set("xmlinput.start", "<measInfo>");
		getConf().set("xmlinput.end", "</measInfo>");
		super.mrTest(rfifs, outputFolder, staticCfgName, cmdClassName, XMLInputFormat.class);
		
		//check results
		//outputFolder should have the csv file
		List<String> files = HdfsUtil.listDfsFile(getFs(), outputFolder);
		logger.info(files);
		String csvFileName = String.format("%s-r-00000", "MyCore_");
		assertTrue(files.contains(csvFileName));
	}
}