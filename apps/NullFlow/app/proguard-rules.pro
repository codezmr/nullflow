# NullFlow — ProGuard rules (release build).
# Room entities/DAOs are handled by the Room compiler's generated adapter.
# Keep the VPN service (referenced from the manifest).
-keep class com.codezmr.nullflow.vpn.FocusVpnService { *; }
