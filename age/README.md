# Age Rotation

Helper to rotate the repository SOPS age key.

## Requirements

- `age-keygen`
- `sops`
- `python3` (3.8+ recommended)
- `SOPS_AGE_KEY_FILE` must be set to the age identity file path before running the script

Example:

```bash
export SOPS_AGE_KEY_FILE=~/Library/Application\ Support/sops/age/keys.txt
```

## Usage

From the repository root run the Python helper (recommended):

```bash
python3 age/rotate_sops_age_key.py
```

What the script does:

1. Generates a new age identity and appends it to the file pointed to by `SOPS_AGE_KEY_FILE` (creates the file if missing).
2. Derives the new public recipient and updates it in `.sops.yaml`.
3. Runs `sops updatekeys` on every `*.sops.yaml` and `*.sops.yml` file under `k8s/` and `k8s_backup/`.

## Quick test (safe, non-committed)

Create a temporary secret, encrypt it, decrypt it, then remove it:

```bash
mkdir -p k8s/tmp
cat > k8s/tmp/test-secret.sops.yaml <<'EOF'
apiVersion: v1
kind: Secret
metadata:
	name: sops-test
stringData:
	token: "test-123"
EOF

sops -e -i k8s/tmp/test-secret.sops.yaml
sops -d k8s/tmp/test-secret.sops.yaml
rm -f k8s/tmp/test-secret.sops.yaml
rmdir k8s/tmp || true
```

## Notes

- Keep the private key file out of Git and in a secure location.
	- The old private key must remain available while `sops updatekeys` runs (so files can be re-encrypted). Only archive/remove the old key once you've verified decryption with the new key.
	- The script requires `SOPS_AGE_KEY_FILE` to be set and passes it through to `sops updatekeys`.
- If `sops -d` fails, point SOPS explicitly at the key file:

```bash
export SOPS_AGE_KEY_FILE=~/Library/Application\ Support/sops/age/keys.txt
sops -d path/to/file.sops.yaml
```