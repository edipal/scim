-- Add JSON columns to scim_users
ALTER TABLE scim_users ADD COLUMN emails JSON;
ALTER TABLE scim_users ADD COLUMN phone_numbers JSON;
ALTER TABLE scim_users ADD COLUMN addresses JSON;
ALTER TABLE scim_users ADD COLUMN entitlements JSON;
ALTER TABLE scim_users ADD COLUMN roles JSON;
ALTER TABLE scim_users ADD COLUMN ims JSON;
ALTER TABLE scim_users ADD COLUMN photos JSON;
ALTER TABLE scim_users ADD COLUMN x509_certificates JSON;

-- Drop the old tables
DROP TABLE scim_user_emails;
DROP TABLE scim_user_phone_numbers;
DROP TABLE scim_user_addresses;
DROP TABLE scim_user_entitlements;
DROP TABLE scim_user_roles;
DROP TABLE scim_user_ims;
DROP TABLE scim_user_photos;
DROP TABLE scim_user_x509_certificates;
