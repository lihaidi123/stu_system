ALTER TABLE iam_account
  ADD COLUMN email VARCHAR(254) NULL,
  ADD COLUMN phone VARCHAR(11) NULL,
  ADD UNIQUE KEY uk_account_email (email),
  ADD UNIQUE KEY uk_account_phone (phone);
