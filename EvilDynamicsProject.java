
public class EvilDynamicsProject {

    public static void main(String[] args) {
        System.out.println("timestep: " + Trial.timestep);
        for (int i = 0; i < 30; ++i) {
            Trial trial = new Trial(i);
            trial.run();
        }
    }

    private static class Trial {

        private static final int n = 100;
//    private static final double error = .05;
        //initial environment
        private static final double pc0 = .01;    //vary this?
        private static final double pmut = .05;     //is this needed...
        //openness
        private static final double o = .5;     //vary this
        //experiment length stuff
        private static final int lengthEncounter = 50;
        private static final int numGens = 4000;
        private static final int timestep = numGens / 20;
        //payoff matrix
        private static final int b = 3, c = 2;     //vary b here
        // this ratio affects which secondary effect wins out...? given our constant tft strat.
        // bc they gain the defect marginal payoff but that misses the bigger from more coop when not going to in tft, b vs c thing?
        private static final Payoff pay = new Payoff(b - c + c, -c + c, b + c, 0 + c);
        //strategies
        private static final Strategy cstrat = new Strategy(false, "tft");
        private static final Strategy dstrat = new Strategy(true, "tft");
        //*****************************************add a blind (control) strat************

        //trial specific vars
        private final Kid[] kids = new Kid[n];
        private long trpnum = 0;
        private long crpnum = 0;
        private long drpnum = 0;
        private final int trialnum;

        public Trial(int tn) {
            trialnum = tn;
        }

        private void run() {
            System.out.print("Trial " + trialnum + "\t");
            setup();
            for (int i = 0; i < numGens; ++i) {
                interactions();
                evolution();
                resetKids();
                if (i % (timestep) == 0) {
                    printDist();
                }
                if (numCoops() == 0 || numCoops() == n) {
//                    System.err.print("stopped after " + i + "\t");
                    break;
                }
            }
            printDist();
            System.out.println("trial " + trialnum + " done");
        }

        private void printDist() {
//            "p cp: " + 
            System.out.print(pCoops() + "\t");
//        for (Kid k: kids) {
//            System.out.println(k);
//        }
        }

        private int numCoops() {
            int count = 0;
            for (Kid k : kids) {
                if (k.strat == cstrat) {
                    ++count;
                }
            }
            return count;
        }

        private double pCoops() {
            return (double) numCoops() / n;
        }

        private void setup() {
            int thresh = (int) (pc0 * n);
//        System.out.println("thresh: " + thresh);
            for (int i = 0; i < thresh; ++i) {
                kids[i] = new Kid(cstrat);
            }
            for (int i = thresh; i < n; ++i) {
                kids[i] = new Kid(dstrat);

            }
        }

        private void resetKids() {
            Kid.resetKidsPay(kids);
            trpnum = 0;
            crpnum = 0;
            drpnum = 0;
        }

        private void interactions() {
            for (int i = 0; i < n; ++i) {
                for (int j = i + 1; j < n; ++j) {
                    encounter(kids[i], kids[j]);
                }
            }
        }

        private void encounter(Kid a, Kid b) {
            Pair alast = null, blast = null;
            for (int i = 0; i < lengthEncounter; ++i) {
                boolean am = a.blindMove(alast);
                boolean bm = b.blindMove(blast);
                if (Math.random() < o) {
                    bm = b.updateMove(am);
                }
                if (Math.random() < o) {
                    am = a.updateMove(bm);
                }
                alast = Pair.fromBools(am, bm);
                blast = Pair.fromBools(bm, am);
                int apay = pay.getPay(alast);
                int bpay = pay.getPay(blast);
                a.addPay(apay);
                b.addPay(bpay);
                if (am) {
                    crpnum += apay;
                } else {
                    drpnum += apay;
                }
                if (bm) {
                    crpnum += bpay;
                } else {
                    drpnum += bpay;
                }
                trpnum += apay + bpay;
            }
        }

        private void evolution() {
//        System.out.println("total payoff: " + trpnum);

            int die = (int) (n * Math.random());
            int rep = -1;
            long num = (int) (trpnum * Math.random()) + 1;
            if (trpnum == 0) {
                rep = 0;
            } else {
                for (int i = 0; i < n; ++i) {
                    Kid k = kids[i];
                    long kpay = k.getPay();
                    if (num <= kpay) {
                        rep = i;
                        break;
                    } else {
                        num -= kpay;
                    }
                }
            }
            if (Math.random() < pmut) {
                kids[die] = kids[rep].makeOtherKid();
            } else {
                kids[die] = kids[rep].makeKid();
            }

//      look at grouped?
            /*int die = (int) (n * Math.random());
        if (Math.random() * trpnum < crpnum) {
            kids[die] = new Kid(cstrat);
        } else {
            kids[die] = new Kid(dstrat);
        }*/
        }
    }

    private static class Kid {

        private Strategy strat;
        private long pay;

        public Kid(Strategy st) {
            strat = st;
            pay = 0;
        }

        public boolean blindMove(Pair past) {
            return strat.blindMove(past);
        }

        public boolean updateMove(boolean coop) {
            return strat.updateMove(coop);
        }

        public void addPay(int dp) {
            pay += dp;
        }

        public long getPay() {
            return pay;
        }

        public void resetPay() {
            pay = 0;
        }

        public Kid makeKid() {
            return new Kid(strat);
        }

        public Kid makeOtherKid() {
            if (strat == Trial.cstrat) {
                return new Kid(Trial.dstrat);
            } else if (strat == Trial.dstrat) {
                return new Kid(Trial.cstrat);
            } else {
                System.err.println("bad");
            }
            return null;
        }

        public static void resetKidsPay(Kid[] kids) {
            for (Kid k : kids) {
                k.resetPay();
            }
        }

        @Override
        public String toString() {
            return strat.toString();
        }
    }

    private static class Strategy {

        private boolean exploit;
        private String basestrat;

        public Strategy(boolean ex, String bs) {
            exploit = ex;
            basestrat = bs;
        }

        //true means cooperate
        public boolean blindMove(Pair past) {
            if (basestrat.equals("tft")) {
                if (past == null) {
                    return Math.random() < .5;
                }
                switch (past) {
                    case cc -> {
                        return true;
                    }
                    case cd -> {
                        return false;
                    }
                    case dc -> {
                        return true;
                    }
                    case dd -> {
                        return false;
                    }
                }
            }
            System.out.println("bad move");
            return false;
        }

        //true is cooperate
        public boolean updateMove(boolean coop) {
            if (coop) {
                return !exploit;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return basestrat + " ex: " + exploit;
        }
    }

    private static class Payoff {

        private int[] pay;

        public Payoff(int cc, int cd, int dc, int dd) {
            int[] p = {cc, cd, dc, dd};
            pay = p;
        }

        public int getPay(Pair pair) {
            return pay[pair.toInt()];
        }

//        System.out.println(pay.getPay(Pair.cc));
//        System.out.println(pay.getPay(Pair.cd));
//        System.out.println(pay.getPay(Pair.dc));
//        System.out.println(pay.getPay(Pair.dd));
    }

    private enum Pair {
        cc(0, "cc"),
        cd(1, "cd"),
        dc(2, "dc"),
        dd(3, "dd");

        private final int myValue;
        private final String mySymbol;

        private Pair(int value, String symbol) {
            myValue = value;
            mySymbol = symbol;
        }

        public static Pair fromBools(boolean coopa, boolean coopb) {
            if (coopa) {
                if (coopb) {
                    return cc;
                } else {
                    return cd;
                }
            } else {
                if (coopb) {
                    return dc;
                } else {
                    return dd;
                }
            }
        }

        public int toInt() {
            return myValue;
        }

        @Override
        public String toString() {
            return mySymbol;
        }

    }

}
