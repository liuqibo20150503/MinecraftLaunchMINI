package mini.launcher;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public interface AuthProvider {

    String username();
    String uuid();
    String accessToken();
    String userType();

    // ============ 离线账户 ============
    class Offline implements AuthProvider {
        private final String name;
        private final String uuid;
        public Offline(String name) {
            this.name = name;
            this.uuid = UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8))
                    .toString().replace("-", "");
        }
        @Override public String username()    { return name; }
        @Override public String uuid()        { return uuid; }
        @Override public String accessToken() { return "0"; }
        @Override public String userType()    { return "legacy"; }
    }

    class Microsoft implements AuthProvider {

        private final String name;
        public Microsoft(String name) { this.name = name; }
        @Override public String username()    { return name; }
        @Override public String uuid()        { return "00000000000000000000000000000000"; }
        @Override public String accessToken() { return "0"; }
        @Override public String userType()    { return "msa"; }
    }
}
