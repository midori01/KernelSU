#ifndef MANAGER_SIGN_H
#define MANAGER_SIGN_H

#define EXPECTED_SIZE 0x0368
#define EXPECTED_HASH "b9ee6759de4794f954883458b722b97ae6527cb7709a051db9a2348c0eea1e42"

typedef struct {
    unsigned size;
    const char *sha256;
} apk_sign_key_t;

#endif /* MANAGER_SIGN_H */
