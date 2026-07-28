-- Weak-point hardening #2 (bounded): tenant_id was a bare varchar with no
-- canonical registry — nothing enforced that a tenant existed, and there was
-- nowhere to hang tenant-level metadata. Introduce a tenants table as the
-- system of record and enforce that every user belongs to a real tenant.
--
-- organization_name stays denormalized on the other tables on purpose: it is a
-- deliberate read-optimization (returned without a join on every query) and
-- shows zero drift in production. This migration adds the registry and the
-- users FK only; it does not strip the denormalized copies. See
-- docs/database-hardening.md.

create table tenants (
    tenant_id         varchar(255) primary key,
    organization_name varchar(255) not null,
    created_at        timestamp with time zone not null
);

-- Backfill from existing users (the identity anchor). No drift, so min() just
-- picks the single consistent organization_name per tenant; earliest user's
-- created_at stands in as the tenant's creation time.
insert into tenants (tenant_id, organization_name, created_at)
select tenant_id, min(organization_name), min(created_at)
from users
group by tenant_id;

-- Every user must reference a real tenant. Runtime signup creates the tenant row
-- before the user (see AuthService.signup); team invites reuse the existing tenant.
alter table users
    add constraint fk_users_tenant
    foreign key (tenant_id) references tenants(tenant_id);
