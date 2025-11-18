#include <stdio.h>
#include <stdarg.h>

int getint() {
    int t;
    scanf("%d", &t);
    return t;
}

void putint(int i) {
    printf("%d", i);
}

void putch(int c) {
    printf("%c", c);
}

void putstr(char *s) {
    printf("%s", s);
}