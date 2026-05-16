import Sortable, {
  type EventType,
  type Group,
  type ScrollSpeed,
  type SortableEvent,
} from 'sortable-dnd';

export type Func = (...args: any[]) => any;

export type SIZETYPE = 'INIT' | 'FIXED' | 'DYNAMIC';

export type DIRECTION = 'FRONT' | 'BEHIND' | 'STATIONARY';

export type MethodNames<T> = {
  [P in keyof T]: T[P] extends (...args: any[]) => any ? P : never;
}[keyof T];

export type MethodType<T, K extends MethodNames<T>> = T[K] extends (...args: any[]) => any
  ? T[K]
  : never;

export type OptionMethodNames<T> = {
  [P in keyof T]: NonNullable<T[P]> extends (...args: any[]) => any ? P : never;
}[keyof T];

export interface Range {
  start: number;
  end: number;
  front: number;
  behind: number;
}

export interface ScrollEvent {
  top: boolean;
  bottom: boolean;
  offset: number;
  direction: DIRECTION;
}

export interface VirtualOptions<T> {
  size?: number;
  keeps: number;
  buffer?: number;
  wrapper: HTMLElement;
  scroller: HTMLElement | Document | Window;
  direction?: 'vertical' | 'horizontal';
  uniqueKeys: T[];
  debounceTime?: number;
  throttleTime?: number;
  onScroll: (event: ScrollEvent) => void;
  onUpdate: (range: Range, changed: boolean) => void;
}

export interface DragEvent<T> {
  key: T;
  index: number;
  event: SortableEvent;
}

export interface DropEvent<T> {
  key: T;
  event: SortableEvent;
  changed: boolean;
  oldIndex: number;
  newIndex: number;
}

export interface SortableOptions<T> {
  delay?: number;
  group?: string | Group;
  handle?: string | ((event: EventType) => boolean);
  lockAxis?: 'x' | 'y';
  disabled?: boolean;
  sortable?: boolean;
  draggable?: string;
  animation?: number;
  autoScroll?: boolean;
  scrollSpeed?: ScrollSpeed;
  appendToBody?: boolean;
  ghostContainer?: HTMLElement | ((sortable: Sortable) => HTMLElement);
  scrollThreshold?: number;
  delayOnTouchOnly?: boolean;
  dropOnAnimationEnd?: boolean;
  ghostClass?: string;
  ghostStyle?: Partial<CSSStyleDeclaration>;
  chosenClass?: string;
  placeholderClass?: string;
  onDrag?: (event: DragEvent<T>) => void;
  onDrop?: (event: DropEvent<T>) => void;
  onChoose?: (event: SortableEvent) => void;
  onUnchoose?: (event: SortableEvent) => void;
}

export type Options<T> = SortableOptions<T> & VirtualOptions<T>;
