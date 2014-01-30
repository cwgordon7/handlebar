package handlebar;

public class PidController {
    protected final double p_;
    protected final double i_;
    protected final double d_;
    protected long last_time;
    protected volatile double last_err;
    protected volatile double cumulative_err;

    public PidController(double p, double i, double d, final ErrorCalculator ec, final ErrorHandler eh) {
        this.p_ = p; // This should be a positive number.
        this.i_ = i; // This should be 0 or near 0.
        this.d_ = d; // This should be used if we are experiencing overshoot.
        (new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    double err = ec.getError();
                    double p_term = p_ * err;
                    long time = System.currentTimeMillis();
                    long dt = time - last_time;
                    cumulative_err += err * dt / 1000.0;
                    double i_term = i_ * cumulative_err;
                    double d_term = d_ * (err - last_err);
                    last_err = err;
                    eh.handleError(p_term + i_term + d_term);
                    Thread.yield();
                }
            }
        })).start();
    }
}
