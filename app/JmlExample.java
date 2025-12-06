class A {
	static int coins;

    //@ ghost int state = 69;
    //@ ghost int estado = 89;

	/*@ requires coins > 0;
	  @ ensures coins > \old(coins);
	  @*/
	static void get_coins()
	{

	}
}
