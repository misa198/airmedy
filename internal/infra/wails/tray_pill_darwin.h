#pragma once
#include <stdbool.h>
void TrayPillCreate(bool showPill, const void *icon, int iconLength);
void TrayPillShowMenu(const char *showLabel, const char *quitLabel);
void TrayPillUpdate(const char *title, const char *artworkPath, const char *trackID, const char *vibrant, const char *muted, bool playing);
void TrayPillSetTheme(const char *theme);
void TrayPillRefreshTheme(void);
void TrayPillPosition(int popupWidth, int *x, int *y);
void TrayPanelShow(int x, int y);
void TrayPanelUpdate(const char *title, const char *artist, const char *artworkPath, bool hasTrack, bool favorite, const char *favoriteLabel);
void TrayPanelSetProgress(double position, double duration, const char *trackID, const char *seekLabel);
void TrayPanelSetIndicator(bool show);
void TrayPanelSetControls(bool playing, bool shuffle, int repeatMode, const char *shuffleLabel, const char *previousLabel, const char *playLabel, const char *nextLabel, const char *repeatLabel);
void TrayPanelSetTheme(const char *theme);
void TrayPanelRefreshTheme(void);
bool TrayPanelIsVisible(void);
void TrayPanelHide(void);
void TrayPanelDestroy(void);
bool TrayPillPointerOverItem(void);
void TrayPillDestroy(void);
