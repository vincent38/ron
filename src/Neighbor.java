public class Neighbor {
    int remoteId;
    boolean visited;
    boolean initSender;

    Neighbor(int r, boolean v) {
        remoteId = r;
        visited = v;
        initSender = false;
    }

    public void setInitSender(boolean initSender) {
        this.initSender = initSender;
    }
}
