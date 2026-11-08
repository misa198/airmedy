#pragma once
#include <stdbool.h>
void TrayPillCreate(bool showPill, const void *icon, int iconLength);
void TrayPillShowMenu(const char *showLabel, const char *quitLabel);
void TrayPillUpdate(const char *title, const char *artworkPath, const char *trackID, const char *vibrant, const char *muted, bool playing);
void TrayPillSetTheme(const char *theme);
void TrayPillRefreshTheme(void);
void TrayPillPosition(int popupWidth, int *x, int *y);
void TrayPillPreparePopup(void *window);
void TrayPillAnimatePopup(void *window);
void TrayPillAnimatePopupOut(void *window);
bool TrayPillPointerOverItem(void);
void TrayPillDestroy(void);
