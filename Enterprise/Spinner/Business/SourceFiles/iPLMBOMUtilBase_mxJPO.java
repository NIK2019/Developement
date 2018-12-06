/* iPLMBOMUtil_mxJPO.java

   Copyright (c) 1992-2010 Dassault Systemes.
   All Rights Reserved.
   This program contains proprietary and trade secret information of MatrixOne,
   Inc.  Copyright notice is precautionary only
   and does not evidence any actual or intended publication of such program
 **************************************************************
 * Modification Details:
 *
 * Ver| Date       | CDSID    | CR  | Comment
 * ---|------------|----------|-----|--------------------------------------
 * 01 |01/12/2017  | rkakde   |     | Moved methods getConnectedEffectivityUsagesToLFRel,getCombinedEffectivityExpression,getDummyEffectivity
 * 01 |01/12/2017  | rkakde   |     | Moved methods appendDummyFeature,getBracketCount,getFeatureMilestoneEffectivityValueFromExpr
 * 02 |06/12/2018  | rashmi  | test | For GIT Hub123
 ** **********************************************************************
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import com.matrixone.apps.configuration.ConfigurationConstants;
import com.matrixone.apps.domain.DomainConstants;
import com.matrixone.apps.domain.DomainObject;
import com.matrixone.apps.domain.DomainRelationship;
import com.matrixone.apps.domain.util.FrameworkUtil;
import com.matrixone.apps.domain.util.MapList;
import com.matrixone.apps.domain.util.MqlUtil;
import com.matrixone.apps.effectivity.EffectivityFramework;
import com.matrixone.apps.engineering.RelToRelUtil;
import com.matrixone.apps.framework.ui.UIUtil;

import matrix.db.Context;
import matrix.db.JPO;
import matrix.db.Policy;
import matrix.util.StringList;
import com.matrixone.apps.productline.ProductLineCommon;

public class iPLMBOMUtilBase_mxJPO extends emxDomainObject_mxJPO implements iPLMConstants_mxJPO{

	public iPLMBOMUtilBase_mxJPO (Context context, String[] args) throws Exception
	{ 
		super(context, args);

	}
	
	public MapList getConnectedEffectivityUsagesToLFRel(Context context,String strNAUsageRelId,BufferedWriter bwNxtAssyUsageLog)throws Exception{
		MapList mlNAUsageDetails = new MapList();
		try{

			StringList relSelects = new StringList();
			relSelects.add("frommid["+ConfigurationConstants.RELATIONSHIP_EFFECTIVY_USAGE+"].id");
			
			DomainConstants.MULTI_VALUE_LIST.add("frommid["+ConfigurationConstants.RELATIONSHIP_EFFECTIVY_USAGE+"].id");
			if(UIUtil.isNotNullAndNotEmpty(strNAUsageRelId)){
				mlNAUsageDetails = DomainRelationship.getInfo(context,new String[]{strNAUsageRelId},relSelects);		
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return mlNAUsageDetails;
	}
	
	public String getCombinedEffectivityExpression (Context context, String strCurrentUsage, String strCADCollectorId)
	{
		String strEffectivity = "";

		try {

			EffectivityFramework ef = new EffectivityFramework();

			if (UIUtil.isNotNullAndNotEmpty(strCADCollectorId))	{

				String strLFId = "";
				String strLFEffectivityExpression = "";
				StringBuffer sbEffectivityExpression = new StringBuffer();
				MapList mlEffectivityExpression = new MapList();
				Map mpEffExprMap = null;
				String strActualValue = "";
				String strUsageChangeRel = "";
				String strLFType = "";

				String strConnectedLFs = MqlUtil.mqlCommand(context, "print bus $1 select $2 dump $3",
						strCADCollectorId, "from[iPLMRelatedFunctionMasterCollector].torel.id", "|");
						
				String strCADCollectorProject = MqlUtil.mqlCommand(context, "print bus $1 select $2 dump $3",
						strCADCollectorId, "project", "|");

				if (UIUtil.isNotNullAndNotEmpty(strConnectedLFs))
				{
					String[] arrLFIds = strConnectedLFs.split("\\|");
					int nLFCount = arrLFIds.length;

					for (int nCount = 0; nCount < nLFCount; nCount++)
					{
						strLFId = arrLFIds[nCount];

						strLFType = MqlUtil.mqlCommand(context, "print connection $1 select $2 dump $3",
								strLFId, "type", "|");
								
						strUsageChangeRel = MqlUtil.mqlCommand(context, "print connection $1 select $2 dump $3",
								strLFId, "tomid[iPLMUsageChange].id", "|");


						mlEffectivityExpression = ef.getRelExpression(context, strLFId); // else get effectivity from the LF itself

						if (!mlEffectivityExpression.isEmpty())
						{
							mpEffExprMap = (Map) mlEffectivityExpression.get(0);
							strActualValue =  (String) mpEffExprMap.get("actualValue");

							if (strLFType.equalsIgnoreCase(RELATIONSHIP_IPLM_LOGICAL_FEATURES_HISTORY) || strLFId.equals(strCurrentUsage)) {
								String strDummyFeatureString = getDummyEffectivity(context, strCADCollectorProject);
								strActualValue = appendDummyFeature(context, strActualValue, strDummyFeatureString).toString();

							}


							if (sbEffectivityExpression.indexOf(strActualValue) == -1) {

								if (sbEffectivityExpression.length() > 0)
								{
									sbEffectivityExpression.append(" OR ");
									sbEffectivityExpression.append("(" + strActualValue + ")");
								} else {
									sbEffectivityExpression.append("(" + strActualValue + ")");
								}
							}
						}

					}
				}

				if (sbEffectivityExpression.length() > 0) {
					strEffectivity = sbEffectivityExpression.toString();

				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return strEffectivity;
	}
	
	public String getDummyEffectivity (Context context, String strVehicleProject)
	{
		StringBuffer sbDummyEffectivity = new StringBuffer();

		if (UIUtil.isNotNullAndNotEmpty(strVehicleProject))
		{
			try {

				iPLMPartBase_mxJPO partBaseObj=new iPLMPartBase_mxJPO(context,null);
				String strCADMaintainedEffectivity="";
				String strConfigOptionName = "JLR"+strVehicleProject;
				String strConfigOptionId = partBaseObj.getEnoviaLogicalFeatureInfo(context,ConfigurationConstants.TYPE_CONFIGURATION_OPTION,strConfigOptionName);
				if(null!=strConfigOptionId && !strConfigOptionId.equals(""))
				{
					String strHardwareProductId=partBaseObj.getEnoviaLogicalFeatureInfo(context,ConfigurationConstants.TYPE_HARDWARE_PRODUCT,strVehicleProject);
					if(null!=strHardwareProductId && !strHardwareProductId.equals(""))
					{
						DomainObject domHardwareObj=DomainObject.newInstance(context,strHardwareProductId);
						String strModelPhysicalId=domHardwareObj.getInfo(context, "to["+ConfigurationConstants.RELATIONSHIP_MAIN_PRODUCT+"].from.physicalid");
						if(null!=strModelPhysicalId && !strModelPhysicalId.equals(""))
						{
							DomainObject domConfigObj=DomainObject.newInstance(context,strConfigOptionId);
							String strConfigOptionRelId=domConfigObj.getInfo(context,"to["+ConfigurationConstants.RELATIONSHIP_CONFIGURATION_OPTIONS+"].physicalid");
							if(null!=strConfigOptionRelId && !strConfigOptionRelId.equals(""))
							{
								sbDummyEffectivity.append("@EF_FO(PHY@EF:");
								sbDummyEffectivity.append(strConfigOptionRelId);
								sbDummyEffectivity.append("~");
								sbDummyEffectivity.append(strModelPhysicalId);
								sbDummyEffectivity.append(")");

							}
						}
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		return sbDummyEffectivity.toString();

	}
	
	public StringBuffer appendDummyFeature(Context context, String strEffectivityExp , String strDummyFeature)
	{
		StringBuffer strFinalExpressionBuff=new StringBuffer();


		String strExistingFeatureEffectivity;
		try 
		{
			String[] ltORSepEffectivity = strEffectivityExp.split("OR");
			
			if(ltORSepEffectivity.length > 1)
				strFinalExpressionBuff.append("(");

			for(int i=0;i<ltORSepEffectivity.length;i++)
			{
				strEffectivityExp = (String) ltORSepEffectivity[i];
				strExistingFeatureEffectivity = getFeatureMilestoneEffectivityValueFromExpr(context, strEffectivityExp,"@EF_MS");		

				if(UIUtil.isNotNullAndNotEmpty(strExistingFeatureEffectivity))
				{
					strFinalExpressionBuff.append(strExistingFeatureEffectivity);		
				}
				if(!strExistingFeatureEffectivity.contains(strDummyFeature))
				{
					if(UIUtil.isNotNullAndNotEmpty(strExistingFeatureEffectivity))
					{
						strFinalExpressionBuff.append(" AND ");
					}
					strFinalExpressionBuff.append(strDummyFeature);
				}
				

				String strExistingMilestoneEffectivity=getFeatureMilestoneEffectivityValueFromExpr(context, strEffectivityExp,"@EF_FO");				
				if(UIUtil.isNotNullAndNotEmpty(strExistingMilestoneEffectivity))
				{
					if(strFinalExpressionBuff.toString().length()>1)
					{
						strFinalExpressionBuff.append(" AND ");
					}

					strFinalExpressionBuff.append(strExistingMilestoneEffectivity);
				}
				
				if(i < ltORSepEffectivity.length-1)
					strFinalExpressionBuff.append(") OR ( ");
			}

			if(ltORSepEffectivity.length > 1)
				strFinalExpressionBuff.append(")");

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		return strFinalExpressionBuff;

	}
	
	public String getFeatureMilestoneEffectivityValueFromExpr(Context context, String strExpr,String strExpression) throws Exception
	{

		StringBuffer strEffectExpressionBuff=new StringBuffer();
		if(null!=strExpr)
		{
			try
			{
				String actualExpression = null;
				String individualCFExpSplit = null;
				actualExpression = strExpr;
				if(ProductLineCommon.isNotNull(actualExpression.trim()))
				{
					StringList strEffecExpLst = FrameworkUtil.split(actualExpression," ");
					for(int i=0;i<strEffecExpLst.size();i++)
					{
						individualCFExpSplit = strEffecExpLst.get(i).toString().trim();
						if(individualCFExpSplit != null && (individualCFExpSplit.equals("(") || individualCFExpSplit.equals(")")))
						{
							strEffectExpressionBuff.append(individualCFExpSplit);
						}

						else if(i!=strEffecExpLst.size()-1 && !strEffecExpLst.get(i+1).toString().contains(strExpression) && !individualCFExpSplit.contains(strExpression) && !individualCFExpSplit.equals(""))
						{
							if(strEffectExpressionBuff.toString().length()>0 && !strEffectExpressionBuff.toString().equals("("))
							{
								strEffectExpressionBuff.append(" "+individualCFExpSplit);
							}
							else if(i!=0 && !strEffecExpLst.get(i-1).toString().contains(strExpression) && !strEffecExpLst.get(i-1).toString().equals(""))
							{
								strEffectExpressionBuff.append(individualCFExpSplit);
							}
							else if(i==0)
							{
								strEffectExpressionBuff.append(individualCFExpSplit);
							}
						}
						else if((i==0 && !individualCFExpSplit.contains(strExpression)) || (i==strEffecExpLst.size()-1 && !individualCFExpSplit.contains(strExpression) && !strEffecExpLst.get(i-1).toString().contains(strExpression)) && !individualCFExpSplit.equals(""))
						{
							strEffectExpressionBuff.append(individualCFExpSplit);
						}
					}
				}
			}
			catch(Exception e)
			{
				throw e;
			}
		}
		String strEffectExpression = strEffectExpressionBuff.toString();

		while(strEffectExpression.contains("()"))
		{
			if(strEffectExpression.contains(" OR ()"))
				strEffectExpression = strEffectExpression.replaceAll(" OR \\(\\)", "");

			if(strEffectExpression.contains(" AND ()"))
				strEffectExpression = strEffectExpression.replaceAll(" AND \\(\\)", "");

			if(strEffectExpression.contains("()"))
				strEffectExpression = strEffectExpression.replaceAll("\\(\\)", "");

			if(strEffectExpression.contains("( OR "))
				strEffectExpression = strEffectExpression.replaceAll("\\( OR ", "\\(");

			if(strEffectExpression.contains("( AND "))
				strEffectExpression = strEffectExpression.replaceAll("\\( AND ", "\\(");

		}
		
		
		if(UIUtil.isNotNullAndNotEmpty(strEffectExpression))
		{
			int iInBrackets = getBracketCount(strEffectExpression,'(');
			int iOutBrackets =  getBracketCount(strEffectExpression,')');
		
			if(iInBrackets < iOutBrackets){
				
				for(int i=0;i<iOutBrackets-iInBrackets;i++)
					strEffectExpression = "(" + strEffectExpression;
				
			} else if(iInBrackets > iOutBrackets){
				
				for(int i=0;i<iInBrackets-iOutBrackets;i++)
					strEffectExpression = strEffectExpression + ")";
			}	
		}
		
		strEffectExpression= strEffectExpression.trim();
		if(UIUtil.isNotNullAndNotEmpty(strEffectExpression) && strEffectExpression.startsWith("(") && strEffectExpression.endsWith(")"))
		{
			strEffectExpression = strEffectExpression.substring(1, strEffectExpression.length()-1);
		}

		return strEffectExpression;
	}
	
	public int getBracketCount(String strExpr, char strBracket)
	{
		int counter = 0;
		for( int i=0; i<strExpr.length(); i++ ) {
		    if( strExpr.charAt(i) == strBracket ) {
		        counter++;
		    } 
		}
		
		return counter;
	}
	
	public void updateRelationshipsandSetBlankEffectivityOnUsage(Context context,String strUsageRelId,BufferedWriter bwSuccessLog,BufferedWriter bwErrorLog) throws Exception 
	{
		String strEffectivityAfferDummyRemove = null;
		EffectivityFramework effFramework =	new EffectivityFramework();
		
		StringBuffer sbEffExp;

		try
		{		
				MapList mlNAUsageDetails = new MapList();
				Map tmpMap = new HashMap();
				Object objEffectivityUsageRelIds;
				StringList slEffectivityUsageRelIds = new StringList();
				
				if(UIUtil.isNotNullAndNotEmpty(strUsageRelId))
				{					
					try{

						mlNAUsageDetails = getConnectedEffectivityUsagesToLFRel(context,strUsageRelId,bwSuccessLog);
						if(mlNAUsageDetails!=null && !mlNAUsageDetails.isEmpty()){
							tmpMap = (Map)mlNAUsageDetails.get(0);
							objEffectivityUsageRelIds = (Object)tmpMap.get("frommid["+ConfigurationConstants.RELATIONSHIP_EFFECTIVY_USAGE+"].id");
							
							if(objEffectivityUsageRelIds instanceof StringList){
								slEffectivityUsageRelIds = (StringList)objEffectivityUsageRelIds;
								
							}
							else
							{
								if(UIUtil.isNotNullAndNotEmpty((String)objEffectivityUsageRelIds))
									slEffectivityUsageRelIds.add((String)objEffectivityUsageRelIds);
									
							}

							if(slEffectivityUsageRelIds != null && !slEffectivityUsageRelIds.isEmpty()){
								loggerMSG(bwSuccessLog,"DISCONNECTING THE FOLLOWING EFFECTIVITY USAGES : "+slEffectivityUsageRelIds);
								//disconnecting the Effectivity Usages
								try{
									DomainRelationship.disconnect(context,(String[])slEffectivityUsageRelIds.toArray(new String[slEffectivityUsageRelIds.size()]));
								} catch (Exception e){
									loggerMSG(bwSuccessLog,"Exception In Disconnect Usage : " + strUsageRelId + " : " + e.getMessage());					
								}
							}
						}
						
						effFramework.updateRelExpression(context, strUsageRelId,"");
						loggerMSG(bwSuccessLog,"Effectivity Expression updated successfully on rel "+strUsageRelId);
					} catch(Exception e)
					{
						loggerMSG(bwErrorLog,"Exception In Operation "+ strUsageRelId +":" + e.getMessage());
						e.printStackTrace();
					}

				}
				else
				{
					loggerMSG(bwErrorLog,"Usage Relationship is Null or Empty " + strUsageRelId );
				}

			}
		catch(Exception e)
		{
			loggerMSG(bwErrorLog,"Exception In Operation " + e.getMessage());
			e.printStackTrace();
		}

	}	
	
	public String builFeatureExpression(Context context,String strFeatureName,String strModelName)throws Exception {
		String strCOCFId = MqlUtil.mqlCommand(context,"temp query bus $4 $1 $5 select $2 dump $3",strFeatureName,"to[Configuration Options].physicalid", "|","Configuration Option","");
		String strPhysicalIdConnection = (String)FrameworkUtil.splitString(strCOCFId,"|").get(3);

		String strModelID = MqlUtil.mqlCommand(context,"temp query bus $4 $1 $5 select $2 dump $3",strModelName,"physicalid","|","Model","");
		String strPhysicalIdModel = (String)FrameworkUtil.splitString(strModelID,"|").get(3);

		return "@EF_FO(PHY@EF:"+strPhysicalIdConnection+"~"+strPhysicalIdModel+")";
	}


	public static void loggerMSG(BufferedWriter bw,String strMessage)
	{

		try {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
			System.out.println("DEBUG : " + strMessage);
			bw.write("["+simpleDateFormat.format(new Date()) + "] : " + strMessage+ "\r\n");
			bw.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void updateEffectivityandDescriptiononCC(Context context, String strUsageRelId,BufferedWriter bwSuccessLog,BufferedWriter bwErrorLog) throws Exception
	{
	  try
	  {
				EffectivityFramework ef = new EffectivityFramework();
				if(UIUtil.isNotNullAndNotEmpty(strUsageRelId))
				{
					
					StringList slRelSelects = new StringList();
					slRelSelects.addElement("from.type");
					slRelSelects.addElement("to.type");
					slRelSelects.addElement("tomid[" + RELATIONSHIP_iPLM_RELATED_FUNCTION_COLLECTOR + "].from.id");
					slRelSelects.addElement("tomid[" + RELATIONSHIP_iPLM_RELATED_FUNCTION_COLLECTOR + "].from.to[Logical Features|from.type==\"iPLMLogicalPartition\"].id");

					StringList slUsageRelSelects = new StringList();
					slUsageRelSelects.addElement(DomainRelationship.SELECT_TYPE);


					MapList mlLFInfo = (MapList)DomainRelationship.getInfo(context, new String[]{strUsageRelId}, slRelSelects);
					Map mapLFInfo = (Map)mlLFInfo.get(0);					

					String strFromSideObjectType = (String)mapLFInfo.get("from.type");
					String strToSideObjectType = (String)mapLFInfo.get("to.type");

					boolean bIsFinalUsage = false;

					if((TYPE_IPLM_LOGICAL_PARTITION.equals(strFromSideObjectType) && TYPE_FUNCTION_PART_MASTER.equals(strToSideObjectType)))
					{
						bIsFinalUsage= true;
						loggerMSG(bwSuccessLog,"strUsageRelId :  " + strUsageRelId + " is identified as Final Usage");
					} 
					
					if(bIsFinalUsage)
					{

						String strCADCollectorId = (String)mapLFInfo.get("tomid[" + RELATIONSHIP_iPLM_RELATED_FUNCTION_COLLECTOR + "].from.id");
						String strLPCCRelId = (String)mapLFInfo.get("tomid[" + RELATIONSHIP_iPLM_RELATED_FUNCTION_COLLECTOR + "].from.to[Logical Features].id");

						boolean bHasDuplicateUsage = false;
						DomainObject domCADCollector;

						if(UIUtil.isNotNullAndNotEmpty(strCADCollectorId))
						{
							try
							{
								loggerMSG(bwSuccessLog,"CAD Collector Object ID : " + strCADCollectorId);
								domCADCollector = new DomainObject(strCADCollectorId);

								StringList ltLFdomCADCollector= domCADCollector.getInfoList(context, "from[" + RELATIONSHIP_iPLM_RELATED_FUNCTION_COLLECTOR + "].torel.id");

								if(ltLFdomCADCollector.size()>1)
								{
									for(int i=0;i<ltLFdomCADCollector.size();i++)
									{
										if(!strUsageRelId.equals((String) ltLFdomCADCollector.get(i)))
										{
											MapList mlUsageLFInfo = (MapList)DomainRelationship.getInfo(context, new String[]{(String) ltLFdomCADCollector.get(i)}, slRelSelects);
											Map mapUsageLFInfo = (Map)mlUsageLFInfo.get(0);	
											String strUsageType = (String)mapUsageLFInfo.get(DomainRelationship.SELECT_TYPE);

											if(RELATIONSHIP_LOGICAL_FEATURES.equalsIgnoreCase(strUsageType))
											{
												bHasDuplicateUsage = true;
												break;
											}
										}
									}
								}

								if(!bHasDuplicateUsage)
								{								
									String strExistingDescription=domCADCollector.getDescription(context);
									if(UIUtil.isNotNullAndNotEmpty(strExistingDescription) && !strExistingDescription.contains("(EFFECTED IN AND OUT AT THE SAME DATE)"))
									{
										String strCollectorDescription="(EFFECTED IN AND OUT AT THE SAME DATE)"+"\n"+strExistingDescription;
										domCADCollector.setDescription(context, strCollectorDescription);
									} else
									{
										domCADCollector.setDescription(context, "(EFFECTED IN AND OUT AT THE SAME DATE)");
									}
									loggerMSG(bwSuccessLog,"Successfully updated CAD collector Description");
								} else
								{
									loggerMSG(bwSuccessLog,"CAD collector identify as 'Duplicate Usage' ");
								}
		
								String strFinalEffectivityExpr = getCombinedEffectivityExpression(context,strUsageRelId,strCADCollectorId);
								ef.setRelExpression(context, strLPCCRelId , strFinalEffectivityExpr);
								loggerMSG(bwSuccessLog,"Successfully updated Effectivity  "+strFinalEffectivityExpr+"on" + strLPCCRelId);
								
							} catch (Exception e)
							{
								loggerMSG(bwErrorLog,"Exception In Updating CAD Collector " + e.getMessage());					
							}

						} 
					}
				}
		}
		catch(Exception e)
		{
			loggerMSG(bwErrorLog,"Exception In Operation " + e.getMessage());
			e.printStackTrace();
		}

	}
	
	public String getCCLastFindNumber(Context context, DomainObject domCC) throws Exception {		
		
		StringList strFindNumberList=domCC.getInfoList(context, "from["+ConfigurationConstants.RELATIONSHIP_LOGICAL_FEATURES+"].attribute["+DomainObject.ATTRIBUTE_FIND_NUMBER+"].value");
		ArrayList findNumberList=new ArrayList();
		int lastFindNumber=1;
		
		if(!strFindNumberList.isEmpty())
		{
			for(int j=0;j<strFindNumberList.size();j++)
			{
				if(!strFindNumberList.get(j).equals(""))
				{
					findNumberList.add(Integer.parseInt((String)strFindNumberList.get(j)));
					Collections.sort(findNumberList);
				}
			}
			if(!findNumberList.isEmpty())
			{
				lastFindNumber= (Integer) findNumberList.get(findNumberList.size()-1);
				lastFindNumber++;
			}
			else
			{
				lastFindNumber=1;
			}
		}
		else
		{
			lastFindNumber=1;
		}
		
		return  Integer.toString(lastFindNumber);
	}
	
	//rkakde : 17031 : Start
	/*
	 * Method to recalculate and modify the effectivity
	 * To execute the method use below format
	 * exec prog iPLMBOMUtilBase -method recalculateAndUpdateCCEffectivity  "<LogFilePath>" "<InputFilePath>";	 
	 */
	public void recalculateAndUpdateCCEffectivity(Context context, String[] args) throws Exception
	{
			
			String strCCID = null;
			String strFormat = "yyyyMMdd_HHmmss";
			String strTimeStamp = new SimpleDateFormat(strFormat).format(Calendar.getInstance().getTime());

			BufferedWriter bwSuccessLog = null;
			BufferedWriter bwErrorLog = null;
			
		try
		{
		
			if (null != args && args.length == 2 && UIUtil.isNotNullAndNotEmpty(args[0]) && UIUtil.isNotNullAndNotEmpty(args[1])){			
				// Success Log
				File fpSuccessLog = new File(args[0] + "recalculateAndUpdateCCEffectivity_Success_"+ strTimeStamp + ".log");
				bwSuccessLog = new BufferedWriter(new FileWriter(fpSuccessLog.getAbsoluteFile()));
				// Error Log
				File fpErrorLog = new File(args[0] + "recalculateAndUpdateCCEffectivity_Error_"+ strTimeStamp + ".log");
				bwErrorLog = new BufferedWriter(new FileWriter(fpErrorLog.getAbsoluteFile()));
				

			} else {
				System.out.println("****PASS LOG FILE PATH****");
				return;
			}

			BufferedReader bufRead = new BufferedReader(new FileReader((String) args[1]));
			String strCurrentLine = bufRead.readLine();

			while(strCurrentLine!= null) 
			{
				strCCID = strCurrentLine.trim();
				updateEffectivityOnCADCollectors(context,strCCID,bwErrorLog,bwSuccessLog);
				strCurrentLine = bufRead.readLine();
			}
		
			
		}catch(Exception e)
		{
			loggerMSG(bwErrorLog,"Exception In Operation " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public void updateEffectivityOnCADCollectors(Context context, String strCADCollectorId,BufferedWriter bwErrorLog,BufferedWriter bwSuccessLog) throws Exception
	{		
		boolean bIsVPMSyncd = true;
		//boolean bIsNotGrouped = false;
		EffectivityFramework ef = new EffectivityFramework();
		String strUpdatedExpression = "",strLFRelIdToCADCollector="",strGCCID="";
		int iIterateCCs,iCADCollectorSize;	
		Map mpLogicalPartition = new HashMap();
		Map mpCCAttributes =new HashMap();
		long startTime = 0;
		long stopTime = 0;
		long elapsedTime = 0;
	
		try {
			startTime = System.currentTimeMillis();
			//iCADCollectorSize = slCADCollectorIds.size();

			StringList slCCSelects = new StringList("project");
			slCCSelects.addElement("to[Logical Features|from.type==\"iPLMLogicalPartition\"].id");

			// Get CAD Maintained attribute and Group CAD Collector Details
			//StringList strSelectableList = new StringList(1);
			//strSelectableList.addElement("to[Logical Features|from.type==\"iPLMLogicalPartition\"].id");
			DomainObject domCADCollectorObj = null;
			//for (iIterateCCs = 0; iIterateCCs < iCADCollectorSize; iIterateCCs++)
			//{
				//strCADCollectorId = (String) slCADCollectorIds.get(iIterateCCs);
				if (UIUtil.isNotNullAndNotEmpty(strCADCollectorId)) 
				{
				
					domCADCollectorObj = new DomainObject(strCADCollectorId);
					mpLogicalPartition = domCADCollectorObj.getInfo(context,slCCSelects);
					
					if(null != mpLogicalPartition && !mpLogicalPartition.isEmpty())
					{
						strLFRelIdToCADCollector = (String)mpLogicalPartition.get("to[Logical Features].id");

					}
					
					strUpdatedExpression = getCCEffectivityExpression(context,strCADCollectorId);
					if (UIUtil.isNotNullAndNotEmpty(strUpdatedExpression))
					{	
						try {
							
							ef.setRelExpression(context, strLFRelIdToCADCollector, strUpdatedExpression);
							loggerMSG(bwSuccessLog,"Updated CAD Collector ------------:"+strCADCollectorId);
							loggerMSG(bwSuccessLog,"Successfully Updated Effectivity Expression : " + strLFRelIdToCADCollector +" : " + strUpdatedExpression);
						} catch (Exception e){
							loggerMSG(bwErrorLog,"Error in CC Effectivity update ------------:"+ strCADCollectorId +" : " +e.getLocalizedMessage());
						}
						
					}
					
				}
			//}

			stopTime = System.currentTimeMillis();
			elapsedTime = stopTime - startTime;
			loggerMSG(bwSuccessLog,"Total Time taken by updateEffectivityOnCADCollectors ------------:"+elapsedTime +" to Updated CAD Collector");
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}		
	}
	
	public String getCCEffectivityExpression (Context context, String strCADCollectorId) throws Exception
	{
		EffectivityFramework ef = new EffectivityFramework();
		String strEffectivityExpression = "",strCADCollectorProject="",strIsCADMaintained="",strLFRelIdToCADCollector="",strGCCID="",strUpdatedExpression="",strDummyFeatureString="";
		MapList mlExprMapList = new MapList();
		Map exprMap = new HashMap();
		int iIterateCCs,iCADCollectorSize;
		StringList relationshipSelects = new StringList();
		boolean bIsVPMSyncd = true;
		Map mpCCAttributes = null;
		try 
		{
			StringList slCCSelects = new StringList("project");
			slCCSelects.addElement("attribute[iPLMCADMaintained]");
			slCCSelects.addElement("from[VPLM Projection]");
			slCCSelects.addElement("to[Logical Features|from.type==\"iPLMLogicalPartition\"].id");
			DomainObject domCADCollectorObj = DomainObject.newInstance(context);
			
			if (UIUtil.isNotNullAndNotEmpty(strCADCollectorId)) 
			{
				domCADCollectorObj.setId(strCADCollectorId);
				mpCCAttributes = (Map)domCADCollectorObj.getInfo(context, slCCSelects);
				if (!mpCCAttributes.isEmpty())
				{
					strIsCADMaintained =(String) mpCCAttributes.get("attribute[iPLMCADMaintained]");
					bIsVPMSyncd = Boolean.parseBoolean((String) mpCCAttributes.get("from[VPLM Projection]"));
					strCADCollectorProject = (String) mpCCAttributes.get("project");
					strLFRelIdToCADCollector = (String) mpCCAttributes.get("to[Logical Features].id");
					
					if( UIUtil.isNotNullAndNotEmpty(strCADCollectorProject))
						strEffectivityExpression = getEffectivityExpressionforLFs(context,strCADCollectorId,strCADCollectorProject);
				}
	
				if (UIUtil.isNullOrEmpty(strEffectivityExpression))
				{
					mlExprMapList = ef.getRelExpression(context, strLFRelIdToCADCollector);
					if (!mlExprMapList.isEmpty())
					{
						exprMap = (HashMap) mlExprMapList.get(0);
						strEffectivityExpression = (String) exprMap.get(EffectivityFramework.ACTUAL_VALUE);
					}
				}
				
				strUpdatedExpression = strEffectivityExpression;
				
				strUpdatedExpression = checkCADMaintainedAndGCC(context, strUpdatedExpression, strCADCollectorProject, strIsCADMaintained, strLFRelIdToCADCollector,
						bIsVPMSyncd,strCADCollectorId);	
						
				strUpdatedExpression = updateServiceEffectivityOnCADCollectors(context, strCADCollectorId,strCADCollectorProject,strUpdatedExpression);
				
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}	
		return strUpdatedExpression;
	}
		public String getEffectivityExpressionforLFs (Context context, String strCADCollectorId,String strCADCollectorProject)
	{
		String strEffectivity = "";
		EffectivityFramework ef = new EffectivityFramework();
		MapList mlEffectivityExpression = new MapList();
		Map mpEffExprMap = null;
		String strActualValue = "";
		String strDummyFeatureString = "";
		StringBuffer sbEffectivityExpression = new StringBuffer();
		String strLFId = "",strConnectedLFs="", strLFType ="";
		MapList mpCCAttributes = null;
		String[] arrLFIds = null;
		int nLFCount,nCount ;
		
		try {
			if (UIUtil.isNotNullAndNotEmpty(strCADCollectorId))	
			{
				strConnectedLFs = MqlUtil.mqlCommand(context, "print bus $1 select $2 dump $3",
				strCADCollectorId, "from[iPLMRelatedFunctionMasterCollector].torel.id","|");
				
				if (UIUtil.isNotNullAndNotEmpty(strConnectedLFs))
				{
					arrLFIds = strConnectedLFs.split("\\|");
					nLFCount = arrLFIds.length;
					
					for ( nCount = 0; nCount < nLFCount; nCount++)
					{
						strLFId = arrLFIds[nCount];
						strLFType = MqlUtil.mqlCommand(context, "print connection $1 select $2 dump $3",
								strLFId, "type", "|");
						
						mlEffectivityExpression = ef.getRelExpression(context, strLFId);
						
												
						if (!mlEffectivityExpression.isEmpty())
						{
							mpEffExprMap = (Map) mlEffectivityExpression.get(0);
							strActualValue =  (String) mpEffExprMap.get("actualValue");
		
							if (strLFType.equalsIgnoreCase(RELATIONSHIP_IPLM_LOGICAL_FEATURES_HISTORY)) 
							{
								strDummyFeatureString = getDummyEffectivity(context, strCADCollectorProject);
								strActualValue = appendDummyFeature(context, strActualValue, strDummyFeatureString).toString();
									if (sbEffectivityExpression.indexOf(strActualValue) == -1) 
									{
										sbEffectivityExpression.append("(" + strActualValue + ")");
															
									}
							}
							else {
								if (sbEffectivityExpression.indexOf(strActualValue) == -1) 
								{
									if (sbEffectivityExpression.length() > 0)
									{
										sbEffectivityExpression.append(" OR ");
										sbEffectivityExpression.append("(" + strActualValue + ")");
										
									} else {
										sbEffectivityExpression.append("(" + strActualValue + ")");
										
									}
								}	
							}
						}
					}
				}
			}
			if (sbEffectivityExpression.length() > 0) 
			{
				strEffectivity = sbEffectivityExpression.toString();
				
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return strEffectivity;
	}
	public String checkCADMaintainedAndGCC (Context context, String strCombinedEffectivity, String strCADCollectorProject,
			String strIsCADMaintained, String strLFRelIdToCADCollector, boolean isVPMSyncd, String strCADCollectorId) {
		try {
			// Check if CAD Maintained
			if (UIUtil.isNotNullAndNotEmpty(strIsCADMaintained) && strIsCADMaintained.equalsIgnoreCase("N")) {
				// Is not CAD Maintained, add CAD maintained feature
				strCombinedEffectivity = addCADMaintainedFeatures(context, strCombinedEffectivity, strCADCollectorProject);
			}
		} catch (Exception e) {

			e.printStackTrace();
		}
		return strCombinedEffectivity;
	}
	public String addCADMaintainedFeatures (Context context, String strCombinedEffectivity, String strCADCollectorProject) {

		StringBuffer sbFinalEffectivity = new StringBuffer();
		try {
			String strCADMaintainedFeature = getCADMaintainedFeature(context, strCADCollectorProject); 
			if (UIUtil.isNotNullAndNotEmpty(strCADMaintainedFeature))
			{
				String strEffectivity  = "";
				if (UIUtil.isNotNullAndNotEmpty(strCombinedEffectivity)) {					
					sbFinalEffectivity = appendDummyFeature(context, strCombinedEffectivity , strCADMaintainedFeature);					
				}  else {
					sbFinalEffectivity.append(strCADMaintainedFeature);
				}
			}
		} catch  (Exception e) {
			// Return the same value
			sbFinalEffectivity = new StringBuffer();
			sbFinalEffectivity.append(strCombinedEffectivity);
			e.printStackTrace();
		}
		return sbFinalEffectivity.toString();
	}
		public String updateServiceEffectivityOnCADCollectors(Context context, String strCADCollectorId,String strCADCollectorProject,String strCombinedEffectivity) throws Exception
	{
		StringList slFunctionClassificationValues = new StringList();
		String strupdatedEffectivity="";
		String sFuncClassValue="";
		try {			
			if (UIUtil.isNotNullAndNotEmpty(strCADCollectorId)) 
			{
				String sFunctionClassificationValues = MqlUtil.mqlCommand(context,"print bus $1 select $2 dump $3", strCADCollectorId,"from[Logical Features].to.from[GBOM].to.attribute[iPLMFunctionClassification].value","|");
				slFunctionClassificationValues = (StringList) FrameworkUtil.split(sFunctionClassificationValues, "|");
					if(slFunctionClassificationValues != null && slFunctionClassificationValues.size() > 0){
						sFuncClassValue = (String) slFunctionClassificationValues.get(0);
						if(UIUtil.isNotNullAndNotEmpty(sFuncClassValue) && sFuncClassValue.equals("S") )
						{		
							strupdatedEffectivity = addServiceFitFeature(context, strCombinedEffectivity,strCADCollectorProject);
						}
						else 
						{
							strupdatedEffectivity = strCombinedEffectivity;
						}
				}	else 
						{
							strupdatedEffectivity = strCombinedEffectivity;
						}			
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}		
		return strupdatedEffectivity;
	}
	
	public String addServiceFitFeature (Context context, String strCombinedEffectivity, String strCADCollectorProject) 
	{
		StringBuffer sbFinalEffectivity = new StringBuffer();
		String strServiceFitFeature = "";
		String[] arrEffectivities = null;
		int nEffectivityCount,nCount ;

		try {
			strServiceFitFeature = getServiceFitFeatureEffectivity(context, strCADCollectorProject); 
			if (UIUtil.isNotNullAndNotEmpty(strServiceFitFeature))
			{
				String strEffectivity  = "";
				if (UIUtil.isNotNullAndNotEmpty(strCombinedEffectivity)){
					sbFinalEffectivity = appendDummyFeature(context, strCombinedEffectivity , strServiceFitFeature);
				}
				else{
					sbFinalEffectivity.append(strServiceFitFeature);
				}
			}
		} catch  (Exception e) {
			// Return the same value
			sbFinalEffectivity = new StringBuffer();
			sbFinalEffectivity.append(strCombinedEffectivity);
			e.printStackTrace();
		}
		return sbFinalEffectivity.toString();
	}
	
	public String getServiceFitFeatureEffectivity(Context context,String strCollectorProjectName) throws Exception
	{
		StringBuffer strEffectivity=new StringBuffer();
		String strConfigOptionName = "Service Fit";
		String strModelPhysicalId= "",strConfigOptionRelId="";
		iPLMPartBase_mxJPO partBaseObj=new iPLMPartBase_mxJPO(context,null);
		String strConfigOptionId=partBaseObj.getEnoviaLogicalFeatureInfo(context,ConfigurationConstants.TYPE_CONFIGURATION_OPTION,strConfigOptionName);
		//String strConfigOptionId=PartExistValidationCheck(context,ConfigurationConstants.TYPE_CONFIGURATION_OPTION,strConfigOptionName,"*");
		if(null!=strConfigOptionId && !strConfigOptionId.equals(""))
		{
			String strHardwareProductId=partBaseObj.getEnoviaLogicalFeatureInfo(context,ConfigurationConstants.TYPE_HARDWARE_PRODUCT,strCollectorProjectName);
			//String strHardwareProductId= PartExistValidationCheck(context,ConfigurationConstants.TYPE_HARDWARE_PRODUCT,strCollectorProjectName,"*");
			
			if(null!=strHardwareProductId && !strHardwareProductId.equals(""))
			{
				DomainObject domHardwareObj=new DomainObject(strHardwareProductId);
				strModelPhysicalId=domHardwareObj.getInfo(context, "to["+ConfigurationConstants.RELATIONSHIP_MAIN_PRODUCT+"].from.physicalid");
				if(null!=strModelPhysicalId && !strModelPhysicalId.equals(""))
				{
					DomainObject domConfigObj=DomainObject.newInstance(context,strConfigOptionId);
					strConfigOptionRelId=domConfigObj.getInfo(context,"to["+ConfigurationConstants.RELATIONSHIP_CONFIGURATION_OPTIONS+"].physicalid");
					if(null!=strConfigOptionRelId && !strConfigOptionRelId.equals(""))
					{
						strEffectivity.append("@EF_FO(PHY@EF:");
						strEffectivity.append(strConfigOptionRelId);
						strEffectivity.append("~");
						strEffectivity.append(strModelPhysicalId);
						strEffectivity.append(")");

					}
				}
			}
		}
		return strEffectivity.toString();
	}
	
	public String getCADMaintainedFeature (Context context, String strProject) {


		String strCADMaintainedEffectivity = "";

		if (UIUtil.isNotNullAndNotEmpty(strProject))
		{

			try {
				iPLMBOMCADCollector_mxJPO iPLMBOMCADCollector = new iPLMBOMCADCollector_mxJPO(context, null);
				strCADMaintainedEffectivity = iPLMBOMCADCollector.getCADMaintainedFeatureEffectivity(context, strProject);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return strCADMaintainedEffectivity;

	}
	
	//rkakde : 17031 : End

}
