package com.classroomgrouping.identity.api;

public final class ProvisioningConflict extends IllegalStateException {
    public ProvisioningConflict() { super("Roster provisioning conflicts with established data; no changes were committed"); }
}
