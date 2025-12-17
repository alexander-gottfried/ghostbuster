class Casino {
    int wallet, bet, amountToBet, coinside, guess;
    //@ ghost int state = 0;

    //@ requires state == 1;
    //@ ensures state == 2;
    //@ requires amountToBet > 0;
    //@ requires wallet >= amountToBet;
    //@ ensures wallet == \old(wallet) - amountToBet;
    void placeBet() {
        bet += amountToBet;
        wallet -= amountToBet;
        //@ set state = 2;
    }

    //@ requires state == 0;
    //@ ensures state == 1;
    //@ ensures bet == 0;
    void createGame() {
        bet = 0;
        //@ set state = 1;
    }

    //@ requires state == 2;
    //@ ensures state == 0;
    //@ ensures \old(coinside) != \old(guess) ==> wallet == \old(wallet);
    //@ ensures \old(coinside) == \old(guess) ==> wallet == \old(wallet)*2;
    void decideBet() {
        if (coinside == guess)
            wallet += 2*bet;
        //@ set state = 0;
    }
}
