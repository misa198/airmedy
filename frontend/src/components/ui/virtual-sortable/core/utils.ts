import type { Func } from './types';

export function throttle(fn: Func, wait: number) {
  let timer: NodeJS.Timeout | null;

  const result = function (this: unknown, ...args: any[]) {
    if (timer) return;

    if (wait <= 0) {
      fn.apply(this, args);
    } else {
      timer = setTimeout(() => {
        timer = null;
        fn.apply(this, args);
      }, wait);
    }
  };

  result['cancel'] = function () {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };

  return result;
}

export function debounce(fn: Func, wait: number) {
  const throttled = throttle(fn, wait);
  const result = function (this: unknown, ...args: any[]) {
    throttled['cancel']();
    throttled.apply(this, args);
  };

  result['cancel'] = function () {
    throttled['cancel']();
  };

  return result;
}

export function isEqual(a: any, b: any) {
  // 0 & -0
  if (a === 0 && b === 0) {
    return 1 / a === 1 / b;
  }

  // NaN
  if (Number.isNaN(a) && Number.isNaN(b)) {
    return true;
  }

  return a == b;
}

export function getDataKey(item: any, dataKey: string | string[]) {
  return (
    !Array.isArray(dataKey) ? dataKey.replace(/\[/g, '.').replace(/\]/g, '.').split('.') : dataKey
  ).reduce((o, k) => (o || {})[k], item);
}

export function elementIsDocumentOrWindow(element: HTMLElement | Document | Window) {
  return (element instanceof Document && element.nodeType === 9) || element instanceof Window;
}
