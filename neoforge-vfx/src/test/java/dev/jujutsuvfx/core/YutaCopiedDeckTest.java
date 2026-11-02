package dev.jujutsuvfx.core;
public final class YutaCopiedDeckTest {
 public static void main(String[] args) throws Exception {
  Class<?> type=Class.forName("dev.jujutsuvfx.core.YutaCopiedDeck");
  var ctor=type.getConstructor(int.class,int.class);var current=type.getMethod("current");
  var launched=type.getMethod("launched");var complete=type.getMethod("complete");
  boolean[][] pairs=new boolean[8][8];
  for(int first=0;first<8;first++)for(int roll=0;roll<7;roll++){
   Object deck=ctor.newInstance(first,roll);int a=(int)current.invoke(deck);
   check(a==first,"first draw preserved");check(!(boolean)complete.invoke(deck),"not complete before release");
   check((int)current.invoke(deck)==a,"retry preserves pending technique");
   launched.invoke(deck);int b=(int)current.invoke(deck);
   check(a!=b&&b>=0&&b<8,"second technique distinct and valid");pairs[a][b]=true;
   check(!(boolean)complete.invoke(deck),"first launch does not skip second");
   launched.invoke(deck);check((boolean)complete.invoke(deck),"both releases complete the guarantee");
   check((int)current.invoke(deck)==a,"subsequent cycles use only selected pair");
  }
  for(int a=0;a<8;a++)for(int b=0;b<8;b++)check(pairs[a][b]==(a!=b),"every ordered distinct pair reachable");
  System.out.println("Yuta copied deck: all 56 pairs distinct, retry stable, both launches required");
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
