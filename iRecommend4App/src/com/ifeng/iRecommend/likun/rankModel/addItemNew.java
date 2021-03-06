/**
 * 
 */
package com.ifeng.iRecommend.likun.rankModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.ScoreDoc;

import com.google.gson.annotations.Expose;
import com.ifeng.commen.Utils.JsonUtils;
import com.ifeng.commen.Utils.k2v;
import com.ifeng.iRecommend.dingjw.front_rankModel.RankItem;
import com.ifeng.iRecommend.dingjw.itemParser.Item;
import com.ifeng.iRecommend.fieldDicts.fieldDicts;
import com.ifeng.iRecommend.likun.locationNews.locationExtraction;
import com.ifeng.iRecommend.usermodel.itemAbstraction;

/**
 * <PRE>
 * 作用 : 
 *   json item document类；方便放入solr的接口中
 *   内容匹配算法的item库用；
 * 使用 : 
 *   
 * 示例 :
 *   
 * 注意 :
 *  由于目前内容体系是所有数据，支持PC、凤凰新闻客户端、凤凰头条多个需求；所以通过other字段做业务和数据分割；
	规则如下：
	1）PC推荐，要求只用other中有ifeng标记的item；
	2）凤凰头条的各种接口，other不用管，都可以用；
	3）凤凰新闻客户端的推荐频道接口，只用other中有ifeng标记的item（这个逻辑还不用做，因为凤凰新闻客户端用的是旧体系，不需要）；分类搜索接口，只用有ifeng标记，或者有yidian标记同时非illegal标记的item

 * 历史 :
 * -----------------------------------------------------------------------------
 *        VERSION          DATE           BY       CHANGE/COMMENT
 * -----------------------------------------------------------------------------
 *          1.0          2014年3月10日        likun          create
 * -----------------------------------------------------------------------------
 * </PRE>
 */

class itemSolrDocNew{
	@Expose
	String itemid;//
	@Expose
	String topic1;
	@Expose
	String topic2;
	@Expose
	String topic3;
	@Expose
	String title;//分词后
	@Expose
	String date;//item published date
	@Expose
	String available;//内容是否还在生命周期内
	@Expose
	String item2app;
	@Expose
	String relatedfeatures;//不可用于表达，但是可索引的feature们
	@Expose
	String source;//稿源
	
	@Expose
	String simID;//全局排重ID
	
	@Expose
	String hotboost;
	
	@Expose
	String doctype;
	
	@Expose
	String other;//扩展字段
	
}

public class addItemNew {
	@Expose
	float boost;
	@Expose
	itemSolrDocNew doc;
	
	/*
	 * 很重的构造函数，执行逻辑包括了：生成topic三层向量、生成item的当前weight
	 */
	public addItemNew(RankItemNew r_item){
		if(r_item == null)
			return;
		doc = new itemSolrDocNew();
		//用oscacheID做后台存储中的关键key,可能是imcp_id，或者url
		doc.itemid = r_item.getItem().getID();
		doc.title = r_item.getItem().getSplitTitle();
		//title去除分词词性
		doc.title = doc.title.replaceAll("_[a-z]+ ", " ");
		
		doc.available = "T";
		doc.topic1 = "";
		doc.topic2 = "";	
		doc.topic3 = "";
		doc.relatedfeatures = "";
		doc.source = r_item.getItem().getSource();
		doc.other = "";
		doc.simID = r_item.getSpecialWords();
		//额外去拼接
		doc.date = r_item.getItem().getPublishedTime().trim();
		if(doc.date != null)
			doc.date = doc.date.replaceFirst(" ", "T").concat("Z");
		
		cmpItemVector(r_item);
		boost = 1.0f;
		
		
		doc.doctype = r_item.getItem().getDocType();
		
	
		
		/*
		 * 这个用于item数据授权控制，函数输出数据分流label，用于不同业务场景下是否可用的授权；比如illegal、yidian、ifeng等等label；
		 */	
		String cla_tags = RankItemNew.genDataAuthLabel(r_item);
		doc.other = cla_tags;
		
		
	}
	
	/**
	 * 计算item的向量表达
	 * <p>
	 * 对item，计算其抽象表达；按一定规则，合并所有item的抽象表达，形成一个统一抽象；并存入topic1、topic2、topic3
	 * 注意：
	 * 其features中只取weight >= 0.5的加入
	 * </p>
	 * 
	 * @param r_item
	 *            item的r_item表达
	 */
	public void cmpItemVector(RankItemNew r_item) {
		if(r_item == null)
			return;
		
		//遍历其features字段，将c按权重放入topic1 sc放入topic2 cn和t放入topic3
		ArrayList<String> al_features = r_item.getItem().getFeatures();
		if(al_features.size()%3 != 0){
			System.out.println("ERROR:features num % 3 != 0");
			return;
		}
		
		//地域识别器
		locationExtraction locEx = new locationExtraction(); 
		HashMap<String, Float> hm_c0c1 = new HashMap<String, Float>();//提取c级别，用于地域新闻过滤
		
		//建模表达
		HashMap<String, Float> hm_tagValues = new HashMap<String, Float>();
		//用于relatedfeatures表达
		HashMap<String, Float> hm_tagValues_rf = new HashMap<String, Float>();
		//逐个处理并得到topic表达；
		String old_type = "";
		for(int i=0;i<al_features.size();i+=3){
			String feature = al_features.get(i);
			String type = al_features.get(i+1);
			float weight = 0f;
			try{
				weight = Float.valueOf(al_features.get(i+2));
			}catch(Exception e){
				weight = 0f;
				e.printStackTrace();
			}
			
			//c < 0
			if((type.equals("c")
					||type.equals("sc")
					||type.equals("cn")
					||type.equals("e")
					||type.equals("s1")
					||type.matches("k[a-z]#")) && weight < 0.5f && Math.abs(weight) > 0.4f){
				hm_tagValues_rf.put(feature, Math.abs(weight));	
				
				if(type.equals("c"))
					hm_c0c1.put(feature, weight);
				
				continue;
			}
			
			
		
			
			//weight太小？
			if(weight < 0.5 && weight > -0.5f)
				continue;
			
			
			if(type.equals("c"))
				hm_c0c1.put(feature, weight);
			
			// loc数据存入rf，也存入t3
			if (type.equals("loc")) {
				hm_tagValues_rf.put("loc=" + feature, Math.abs(weight));
			}

		

			weight = Math.abs(weight);
			
			type = judge_T_what(type);
			
			if(type.equals(old_type)){
				hm_tagValues.put(feature, weight);
			}else if(hm_tagValues.size() > 0){//将前一层级的特征表达转入topic字段
				turnVectorToDoc(old_type,hm_tagValues);
				hm_tagValues.clear();
				hm_tagValues.put(feature, weight);
				old_type = type;
			}else{
				hm_tagValues.put(feature, weight);
				old_type = type;
			}
		}
		
		if(hm_tagValues.size() > 0){//将前一层级的特征表达转入topic字段
			turnVectorToDoc(old_type,hm_tagValues);
		}
		
//		//对ifeng内容做地域识别映射；如果从表达中识别出地域映射关系，成为本地数据；这部分数据不加入loclist，正常投放；注意，地域内容过滤掉国际、台湾、军事、汽车、明星、股票类型本地数据
//		String url = r_item.getItem().getUrl();
//		if(url != null && url.indexOf("ifeng.com") > 0){
//			if((hm_c0c1.get("国际") == null || Math.abs(hm_c0c1.get("国际")) <= 0.3f)
//					&& (hm_c0c1.get("台湾") == null || Math.abs(hm_c0c1.get("台湾")) <= 0.3f)
//					&& (hm_c0c1.get("股市") == null || Math.abs(hm_c0c1.get("股市")) <= 0.3f)
//					&& (hm_c0c1.get("娱乐") == null || Math.abs(hm_c0c1.get("娱乐")) <= 0.3f)
//					&& (hm_c0c1.get("体育") == null || Math.abs(hm_c0c1.get("体育")) <= 0.3f)
//					&& (hm_c0c1.get("汽车") == null || Math.abs(hm_c0c1.get("汽车")) <= 0.3f)
//					&& (hm_c0c1.get("军事") == null || Math.abs(hm_c0c1.get("军事")) <= 0.3f)){
//				String loc = locEx.extractLocation(r_item.getItem());
//				if (loc != null && !loc.isEmpty()) {
//					hm_tagValues_rf.put("loc=" + loc, 1.0f);
//				}
//				
//			}
//		}
	
		if(hm_tagValues_rf.size() > 0){//将只可索引的特征表达转入topic字段
			turnVectorToDoc("rf",hm_tagValues_rf);
		}
	}
	
	/*
	 * 特征分类体系到t1、t2、t3的映射
	 * @param feature_type 
	 * 		feature的分类
	 */
	public static String judge_T_what(String feature_type){
		if(feature_type == null || feature_type.isEmpty())
			return "";
		if(feature_type.equals("c"))
			return "t1";
		if(feature_type.equals("sc")||feature_type.equals("cn")||feature_type.equals("t")
				||feature_type.equals("e")
				||feature_type.equals("s1"))
			return "t2";
		if(feature_type.startsWith("k")
				||feature_type.startsWith("et")
				||feature_type.startsWith("n")
				||feature_type.equals("x")
				||feature_type.equals("loc"))
			return "t3";
		return "t3";
	}
	
	
//	/*
//	 * vector的document化描述
//	 * tag按照w来描述其个数；tag之间以空格分开；
//	 * 注意：
//	 * 为了doc化表达，默认看下value的最小值（绝对值，因为小于0表示不可读，但是仍然可以表达），如果是<1.0，那么默认所有value都乘以某个最小的整数，使这个最小值能>=1
//	 * 这样实现了尽可能的doc化表达
//	 * (non-Javadoc)
//	 * 
//	 */
//	private void turnVectorToDoc(String t_type, HashMap<String, Float> hm_tags){
//		if(hm_tags == null || t_type == null || t_type.isEmpty())
//			return;
//		
//		
////		//算最小weight
////		float min_w = 1;
////		Iterator<Entry<String, Float>> it = hm_tags.entrySet().iterator();
////		while(it.hasNext()){
////			float w = it.next().getValue();
////			if(min_w > Math.abs(w))
////				min_w = w;
////		}
////		
////		//取最小倍数，实现所有整数化表达
////		int temp = 1;
////		if(min_w >0 && min_w <1)
////			temp = (int) (1/min_w);
//		
//		int doc_ex_factor = 10;
//		if(t_type.equals("rf"))
//			doc_ex_factor = 1;
//		
//		//根据现有表达[-1,1]，统一乘以10，求得文档化表达；
//		StringBuffer sbRes = new StringBuffer();
//		Iterator<Entry<String, Float>> it = hm_tags.entrySet().iterator();
//		while(it.hasNext()){
//			Entry<String, Float> et = it.next();
//			int tagTF = (int) ( Math.abs(et.getValue())*doc_ex_factor);
//			for(int i=0;i<tagTF;i++){
//				sbRes.append(et.getKey()).append(" ");
//			}
//			
//		}
//		if(t_type.equals("rf"))
//			this.doc.relatedfeatures =  sbRes.toString().trim();	
//		else if(t_type.equals("t1"))
//			this.doc.topic1 =  sbRes.append(this.doc.topic1).append(" ").toString().trim();	
//		else if(t_type.equals("t2"))
//			this.doc.topic2 =  sbRes.append(this.doc.topic2).append(" ").toString().trim();	
//		else if(t_type.equals("t3"))
//			this.doc.topic3 =  sbRes.append(this.doc.topic3).append(" ").toString().trim();	
//
//	}

	

	/*
	 * vector的document化描述
	 * tag能够按weight来表达，不再做doc转化
	 * (non-Javadoc)
	 * 
	 */
	private void turnVectorToDoc(String t_type, HashMap<String, Float> hm_tags){
		if(hm_tags == null || t_type == null || t_type.isEmpty())
			return;

		StringBuffer sbRes = new StringBuffer();
		Iterator<Entry<String, Float>> it = hm_tags.entrySet().iterator();
		while(it.hasNext()){
			Entry<String, Float> et = it.next();
			sbRes.append(et.getKey()).append("^").append(et.getValue()).append(" ");
		}
		if(t_type.equals("rf"))
			this.doc.relatedfeatures =  sbRes.toString().trim();	
		else if(t_type.equals("t1"))
			this.doc.topic1 =  sbRes.append(this.doc.topic1).append(" ").toString().trim();	
		else if(t_type.equals("t2"))
			this.doc.topic2 =  sbRes.append(this.doc.topic2).append(" ").toString().trim();	
		else if(t_type.equals("t3"))
			this.doc.topic3 =  sbRes.append(this.doc.topic3).append(" ").toString().trim();	

	}
}
