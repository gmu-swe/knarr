package anon.knarr.server.concolic;

import anon.knarr.server.Canonizer;
import anon.knarr.server.ConstraintServerHandler;
import anon.knarr.server.concolic.picker.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PickerEval {

    private static final String SEPARATOR = ";";

    public static void main(String args[]) throws IOException {
        Concolic c = new Concolic();

        c.startConstraintServer();

        switch (args[0]) {
            case "http":
                c.setHTTPDriver();
                break;
            case "int":
                c.setIntDriver();
                break;
            case "byte":
                c.setByteDriver();
                break;
            default:
                throw new Error("Unknown drive: " + args[0]);
        }

        HashMap<File, HashMap<Picker, Score>> results = new HashMap<>();

        Picker[] ps = new Picker[] {
                new MaxPathsPicker(),
                new MaxConstraintsPicker(),
                new MaxStaticCoveragePicker(),
                new MaxBytecodePicker(),
                new MaxDistancePicker(),
                new MaxMeanConstraintsPicker(),
        };

        LinkedList<File> order = new LinkedList<>();
        order.addAll(Arrays.asList(new File(args[1]).listFiles()));

        Collections.sort(order, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });


        for (File f : order) {
            HashMap<Picker, Score> map = new HashMap<>();

            for (Picker p : ps) {
                Score s = addInput(f, p, c);
                map.put(p, s);
            }

            results.put(f, map);
        }

        if (args.length > 2) {
            File aflCsv = new File(args[2]);

            Picker afl = new AFL();
            Picker[] newPickers = new Picker[ps.length + 1];
            System.arraycopy(ps, 0, newPickers, 0, ps.length);
            ps = newPickers;
            ps[ps.length - 1] = afl;

            try (BufferedReader br = new BufferedReader(new FileReader(aflCsv))) {
                String line;

                Pattern p = Pattern.compile(".*orig:(.*)");

                while ((line = br.readLine()) != null) {
                    String[] cols  = line.split(";");

                    Matcher m = p.matcher(cols[0]);
                    if (!m.matches())
                        continue;
                    String name = m.group(1);
                    int score = Integer.parseInt(cols[3].trim());

                    File a = new File(args[1], name);
                    HashMap<Picker, Score> r = results.get(a);

                    if (r != null)
                        r.put(afl, new Score(score, "afl"));
                }
            }
        }

        System.out.print("input" + SEPARATOR + "exec time" + SEPARATOR);
        for (Picker p : ps) {
            System.out.print(p.getClass().getSimpleName());
            System.out.print(" score" + SEPARATOR);
            System.out.print(p.getClass().getSimpleName());
            System.out.print(" reason" + SEPARATOR);
        }
        System.out.println();


//        for (Map.Entry<File, HashMap<Picker, Score>> entry : results.entrySet()) {
        for (File f : order) {

            System.out.print(f.getName());
            System.out.print(SEPARATOR);

            System.out.print(f.getName().replaceAll("_.*",""));
            System.out.print(SEPARATOR);

            for (Picker p : ps) {
                Score s = results.get(f).get(p);
                System.out.print(s.value);
                String reason = SEPARATOR + ((s.reason == null) ? "rejected" : s.reason) + SEPARATOR;
                System.out.print(reason);
            }

            System.out.println();
        }

    }

    private static Score addInput(File f, Picker p, Concolic c) throws IOException {
        Object data = c.driver.fromFile(f);

        ConstraintServerHandler server = c.driver.drive(data);

        if (server == null)
            throw new Error("Input " + f + " failed");

        Input in = new Input();
        in.constraints = new Canonizer();
        in.constraints.canonize(server.req);
        in.coverage = server.cov;
        in.input = data;
        p.score(in);

        String reason = p.saveInput(null, in);

        return new Score(in.score, reason);
    }

    private static class Score {
        final double value;
        final String reason;

        public Score(double value, String reason) {
            this.value = value;
            this.reason = reason;
        }
    }

    private static class AFL extends Picker {

        @Override
        protected Input doPickInput(Collection<Input> inputs) { throw new Error(); }

        @Override
        protected String shouldSaveInput(Input in, Collection<Input> ins) {
            throw new Error();
        }

        @Override
        protected Collection<Input> createInCirculation() {
            return new HashSet<>();
        }
    }

}
