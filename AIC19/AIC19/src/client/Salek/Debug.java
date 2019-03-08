package client.Salek;

public class Debug {

        public void timing() {

            long startTime = System.nanoTime();

            try {
                System.out.println();
            } finally {
                long endTime = System.nanoTime();
                long duration = (endTime - startTime);
                System.out.println("___it took: " + duration);
            }
        }

}
