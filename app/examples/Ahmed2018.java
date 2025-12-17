public class Process {
    //@ ghost int STATE = 1;
    //@ invariant 1 <= STATE && STATE <= 7;

    /*@
      requires STATE == 1;
      ensures STATE == 2;*/
    public void Admit();

    /*@
      requires STATE == 2;
      ensures STATE == 6;*/
    public void Run();

    /*@
      requires STATE == 6;
      ensures STATE == 7;*/
    public void Terminate();

    /*@
      requires STATE == 6;
      ensures STATE == 4;*/
    public void Block();

    /*@
      requires STATE == 4 || STATE == 5;
      ensures \old(STATE) == 4 ==> STATE == 2;
      ensures \old(STATE) == 5 ==> STATE == 3; */
    public void Unblock();

    /*@
      requires STATE == 2 || STATE == 4;
      ensures \old(STATE) == 2 ==> STATE == 3;
      ensures \old(STATE) == 4 ==> STATE == 5; */
    public void Suspend();

    /*@
      requires STATE == 3 || STATE == 5;
      ensures \old(STATE) == 3 ==> STATE == 2;
      ensures \old(STATE) == 5 ==> STATE == 4;*/
    public void Resume();

    /*@
      requires STATE == 6;
      ensures STATE == 2;*/
    public short Preempt();

    public int GetStatus();

    public int GetProcessID();

}
