package dev.jujutsuvfx.core;
/** Two distinct draws; only a real release advances the sequence, so canceled charges retry. */
public final class YutaCopiedDeck {
 public static final int BLUE=0,RED=1,SMALL_PURPLE=2,LARGE_PURPLE=3,SPEECH=4,FUGA=5,DISMANTLE=6,CLEAVE=7,COUNT=8;
 private final int first,second;
 private int releases;
 public YutaCopiedDeck(int first,int secondRoll){
  if(first<0||first>=COUNT||secondRoll<0||secondRoll>=COUNT-1)throw new IllegalArgumentException("Invalid copied technique draw");
  this.first=first;second=secondRoll>=first?secondRoll+1:secondRoll;
 }
 public int current(){return (releases&1)==0?first:second;}
 public void launched(){if(releases<Integer.MAX_VALUE)releases++;else releases=2;}
 public boolean complete(){return releases>=2;}
}
