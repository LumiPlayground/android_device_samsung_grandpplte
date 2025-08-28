/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <pthread.h>

pid_t __pthread_gettid(pthread_t t) {
    return pthread_gettid_np(t);
}
