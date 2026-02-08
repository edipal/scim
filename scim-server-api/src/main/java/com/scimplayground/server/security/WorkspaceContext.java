package com.scimplayground.server.security;

import com.scimplayground.server.model.Workspace;
import com.scimplayground.server.model.WorkspaceToken;

/**
 * Thread-local holder for the current workspace context resolved from auth.
 */
public class WorkspaceContext {

    private static final ThreadLocal<WorkspaceContextData> CONTEXT = new ThreadLocal<>();

    public static void set(Workspace workspace, WorkspaceToken token) {
        CONTEXT.set(new WorkspaceContextData(workspace, token));
    }

    public static Workspace getWorkspace() {
        WorkspaceContextData data = CONTEXT.get();
        return data != null ? data.workspace : null;
    }

    public static WorkspaceToken getToken() {
        WorkspaceContextData data = CONTEXT.get();
        return data != null ? data.token : null;
    }

    public static void clear() {
        CONTEXT.remove();
    }

    private record WorkspaceContextData(Workspace workspace, WorkspaceToken token) {}
}
