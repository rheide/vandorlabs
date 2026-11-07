package com.vandorlabs.programmer;

/** Released programmer decisions over bounded credential descriptors; no inventory ownership. */
public final class ProgrammerPolicy {
 public static final int EMPTY=0,CARD=1,CHIP=2,OTHER=3;
 private ProgrammerPolicy(){}
 public static boolean token(int kind,int company){return (kind==CARD||kind==CHIP)&&company>=0&&company<7;}
 public static int company(int kind,int company,boolean masterChip){return company>0&&company<7&&(kind==CARD||kind==CHIP&&masterChip)?company:0;}
 public static boolean validSlot(int slot,int kind,int company,boolean masterChip){return token(kind,company)&&(slot!=0||company>0&&(kind==CARD||masterChip));}
 public static String problem(int inputKind,int inputCompany,int inputCount,boolean outputPresent,boolean masterPresent,int company,boolean journal){
  if(inputKind==EMPTY)return "Insert an input card or chip";
  if(!token(inputKind,inputCompany)||inputCount!=1)return "Input must contain one card or chip";
  if(outputPresent)return "Remove the output item first";
  if(masterPresent&&company==0)return "Master needs a company card or Master Data Chip";
  if(inputKind==CARD&&company==0&&inputCompany==0)return "Insert a company master";
  if(inputKind==CHIP&&!journal&&company==0&&inputCompany==0)return "Master chips require a company master";
  return "";
 }
 public static String copyProblem(int sourceKind,int sourceCompany,int sourceCount,int targetKind,int targetCompany,int targetCount){
  if(sourceKind==EMPTY||targetKind==EMPTY)return "Insert both Input and Output items to copy";
  if(!token(sourceKind,sourceCompany)||!token(targetKind,targetCompany)||sourceCount!=1||targetCount!=1)return "Copy requires one valid item in each slot";
  return sourceKind==targetKind?"":"Copy needs two cards or two chips";
 }
}
