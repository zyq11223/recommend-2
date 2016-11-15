package com.ifeng.iRecommend.likun.userCenter.tnappuc.utils;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.ifeng.iRecommend.featureEngineering.dataStructure.itemf;
import com.ifeng.ikvlite.IkvLiteClient;

public class ItemIKVUtil {
	static Logger LOG = Logger.getLogger(ItemIKVUtil.class);
	// 访问 10.32.25.21或者10.32.25.22
	private static String HOST0 = "10.32.25.21";
	private static String HOST1 = "10.32.25.22";
	
//	String host0 = "10.32.25.21";
//    String host1 = "10.32.25.22";
//   
//    IkvLiteClient client =  new IkvLiteClient( keyspace,table);
//    client.connect(host0, host1);
	
	
	private static final String KEYSPACE = "ikv";
	private static String[] defaultTables = {"appitemdb", "appbilldb" };
	private static IkvLiteClient client;
	private static String tablename = "appitemdb";

	public static void init() {
		try {
			client = new IkvLiteClient(KEYSPACE, tablename,true);
			client.connect(HOST0, HOST1);
			LOG.info("IKV client create success.");
		} catch (Exception e) {
			LOG.error("IKV client create failed.", e);
			return;
		}
		
	}

	public static String get(String key) {
		if (key == null)
			return null;
		String value = "";
		try {
			value = client.get(key);
		} catch (Exception e) {
			LOG.error("ERROR get: " + key);
			LOG.error("ERROR get", e);
			return null;
		}
		return value;
	}

	public static Map<String, String> gets(String key) {
		if (key == null)
			return null;
		Map<String, String> retmap = new HashMap<String, String>();
		try {
			retmap = client.gets(key);
		} catch (Exception e) {
			LOG.error("ERROR get: " + key);
			LOG.error("ERROR get", e);
			return null;
		}
		return retmap;
	}

	public static void put(String key, String value) {
		if (key == null)
			return;
		if (value == null)
			return;
		try {
			client.put(key, value);
//			LOG.info("Successed set key " + key + "\t" + value);
		} catch (Exception e) {
			LOG.error("ERROR put key: " + key);
			LOG.error("ERROR put toJson", e);
			return;
		}
	}

	/**
	 * 在IKV中删除tag type 注意：
	 * 
	 * @param key
	 *            ，
	 * @return
	 */
	public static void del(String key) {
		if (key != null)
		{
			try{
			client.delete(key);
			}catch(Exception e){
				LOG.error("Delete "+key+" failed.");
			}
		}
			
		LOG.info("Delete key "+key+" success.");
	}
	
	public static void close(){
		client.close();
		LOG.info("Client to  has closed.");
	}
	
	/**
	 * 查询itemf only for cmpp and xml item
	 * @param key	 key是title，url或者id。title和url是到id的索引，进行二次查询
	 * @param type  查询的类型，用来区分id的来源，x表示xml，c表示cmpp, d表示默认（用于标题或url查询或者未知类型时将随机选取结果）
	 * @return itemf，item及其特征表达类
	 */
	public static itemf queryItemF(String key, String type) {
		Gson gson = new Gson();
		if (key == null || key.isEmpty())
			return null;
		if(!type.equals("c")&&!type.equals("x")&&!type.equals("d"))
			LOG.error("Input type is error, return. "+key);
		itemf item = null;
		String json = null;
		if (isNumeric(key) && (type.equals("c")|| type.equals("x"))) {
			json = get(type+"_"+key);
			item = gson.fromJson(json, itemf.class);
		} else if(isNumeric(key) && type.equals("d")){
			json = get("c_"+key);
			if(json==null || json.isEmpty())
				json = get("x_"+key);
			if(json==null || json.isEmpty())
				return null;
			item = gson.fromJson(json, itemf.class);
		}
		else {
			String tempId = null;
			tempId = get(key);
			if(null == tempId||tempId.equals(""))
				return null;
			json = get("c_"+tempId);
			if(json == null || json.isEmpty())
				json = get("x_"+tempId);
			if(json == null || json.isEmpty())
				return null;
			item = gson.fromJson(json, itemf.class);
		}
		return item;
	}
	
	/**
	 * 查询appitem
	 * @param key	 key是title，url或者id。title和url是到id的索引，进行二次查询
	 * @param type  查询的类型，用来区分id的来源，x表示xml，c表示cmpp
	 * @return appitem其特征表达类
	 */
	/*public appBill queryAppBill(String key, String type) {
		if (key == null || key.isEmpty())
			return null;
		if(!type.equals("c")&&!type.equals("x")&&!type.equals("d"))
			LOG.error("Input type is error, return. "+key);
		appBill appitem = null;
		String json = null;
		if (isNumeric(key) && (type.equals("c")|| type.equals("x"))) {
			json = get(type+"_"+key);
			appitem = JsonUtils.fromJson(json, appBill.class);
		} else if(isNumeric(key) && type.equals("d")){
			json = get("c_"+key);
			if(json==null || json.isEmpty())
				json = get("x_"+key);
			if(json==null || json.isEmpty())
				return null;
			appitem = JsonUtils.fromJson(json, appBill.class);
		}
		else {
			String tempId = null;
			tempId = get(key);
			if(null == tempId||tempId.equals(""))
				return null;
			json = get("c_"+tempId);
			if(json == null || json.isEmpty())
				json = get("x_"+tempId);
			if(json == null || json.isEmpty())
				return null;
			appitem = JsonUtils.fromJson(json, appBill.class);
		}
		return appitem;
	}*/
	/**
	 * 判断是否为数字字符串
	 * @param str
	 * @return
	 */
	private static boolean isNumeric(String str) {
		if (str == null) {
			return false;
		}
		int sz = str.length();
		for (int i = 0; i < sz; i++) {
			if (Character.isDigit(str.charAt(i)) == false) {
				return false;
			}
		}
		return true;
	}
	public static void main(String[] args){
		init();
		String key = "042eec0e-1efc-4a60-a6b0-8d9124751c3e";
		itemf i = queryItemF(key,"d");
		System.out.println();
	}

}
