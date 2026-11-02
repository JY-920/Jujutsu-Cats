package dev.jujutsuvfx.core;

public final class GetoMeleeSequenceTest {
 public static void main(String[] args)throws Exception{
  Class<?> type;
  try{type=Class.forName("dev.jujutsuvfx.core.GetoMeleeSequence");}
  catch(ClassNotFoundException e){throw new AssertionError("Geto has no committed melee combinations",e);}
  var begin=type.getMethod("begin",int.class);var action=type.getMethod("action");
  var advance=type.getMethod("advance");var active=type.getMethod("active");var cancel=type.getMethod("cancel");
  var disengage=type.getMethod("canDisengage",int.class,double.class,double.class);
  var moves=new java.util.HashSet<Integer>();
  for(int variant=0;variant<4;variant++){
   Object sequence=type.getConstructor().newInstance();begin.invoke(sequence,variant);
   int count=0,last=-1;
   do{
    int move=(int)action.invoke(sequence);moves.add(move);
    check(move!=last,"two adjacent strokes must change direction or technique");last=move;count++;
   }while((boolean)advance.invoke(sequence));
   check(count>=3&&count<=4,"close melee is a short combination, never just one stroke");
   check(!(boolean)active.invoke(sequence),"combo ends instead of locking combat forever");
   begin.invoke(sequence,variant);cancel.invoke(sequence);
   check(!(boolean)advance.invoke(sequence)&&!(boolean)active.invoke(sequence),"guard/spell interruption drops queued strikes");
  }
  check(moves.size()==7,"all seven staff moves are selected in real combinations");
  check(!(boolean)disengage.invoke(null,0,3.0,0.0),"first close encounter cannot immediately backstep and beam");
  check(!(boolean)disengage.invoke(null,1,5.0,0.0),"one combo does not force the single-melee then beam loop");
  check((boolean)disengage.invoke(null,2,5.0,0.0),"ranged spell remains available after close exchanges");
  check((boolean)disengage.invoke(null,0,14.0,0.0),"a distant target can still be engaged without melee");
  check((boolean)disengage.invoke(null,0,5.0,4.0),"airborne opponents do not stall melee-only AI");
  System.out.println("Geto combinations: seven moves, 3-4 strokes, interruptions and distance-aware ranged decisions passed");
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
