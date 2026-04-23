-- Create logs table for application-wide logging with tenant isolation

CREATE TABLE IF NOT EXISTS public.logs (
    id UUID PRIMARY KEY NOT NULL,
    tenant_id UUID NOT NULL REFERENCES public.tenants(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.users(id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_logs_tenant 
        FOREIGN KEY (tenant_id) 
        REFERENCES public.tenants(id) ON DELETE CASCADE,
    
    CONSTRAINT fk_logs_user 
        FOREIGN KEY (user_id) 
        REFERENCES public.users(id) ON DELETE SET NULL
);

-- Create indexes for optimized queries
CREATE INDEX IF NOT EXISTS idx_logs_tenant_id ON public.logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_logs_user_id ON public.logs(user_id);
CREATE INDEX IF NOT EXISTS idx_logs_created_at ON public.logs(created_at);
CREATE INDEX IF NOT EXISTS idx_logs_tenant_created ON public.logs(tenant_id, created_at);

-- Add comment for clarity
COMMENT ON TABLE public.logs IS 'Application-wide activity logging with UUID-based multi-tenant isolation via tenant_id column';
COMMENT ON COLUMN public.logs.tenant_id IS 'Tenant identifier for multi-tenant isolation - part of composite filter';
COMMENT ON COLUMN public.logs.action IS 'Description of the action performed (e.g., "USER_CREATED", "PAYMENT_PROCESSED")';
