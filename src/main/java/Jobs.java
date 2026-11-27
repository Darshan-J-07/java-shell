import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

final class Jobs {
    private record Job(int id, Pipeline pipeline, String command) {}

    private final List<Job> active = new ArrayList<>();

    void add(Pipeline pipeline, String command, OutputStream out) throws IOException {
        int id = active.isEmpty() ? 1 : active.getLast().id() + 1;
        Shell.print(out, "[" + id + "]" + pipeline.pidSuffix());
        active.add(new Job(id, pipeline, command));
    }

    void reap(OutputStream out) throws IOException {
        report(out, false);
    }

    void list(OutputStream out) throws IOException {
        report(out, true);
    }

    private void report(OutputStream out, boolean includeRunning) throws IOException {
        List<Job> completed = new ArrayList<>();
        for (int index = 0; index < active.size(); index++) {
            Job job = active.get(index);
            if (job.pipeline().isRunning()) {
                if (includeRunning) {
                    describe(out, job, marker(index), "Running", job.command() + " &");
                }
            } else {
                describe(out, job, marker(index), "Done", job.command());
                completed.add(job);
            }
        }
        // Keep job numbers and markers stable throughout a single report.
        active.removeAll(completed);
    }

    private char marker(int index) {
        if (index == active.size() - 1) {
            return '+';
        }
        if (index == active.size() - 2) {
            return '-';
        }
        return ' ';
    }

    private static void describe(OutputStream out, Job job, char marker, String status, String command)
            throws IOException {
        Shell.print(out, String.format("[%d]%c  %-24s%s", job.id(), marker, status, command));
    }

    void close() {
        Pipeline.stopAll(active.stream().map(Job::pipeline).toList());
        active.clear();
    }
}
