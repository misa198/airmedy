import Sortable, { type SortableOptions as DndOptions, type SortableEvent } from 'sortable-dnd';
import type {
  DropEvent,
  MethodNames,
  MethodType,
  OptionMethodNames,
  Options,
  Range,
  ScrollEvent,
  VirtualOptions,
} from './types';
import { Virtual } from './Virtual';
import { isEqual } from './utils';

const SortableAttrs = [
  'delay',
  'group',
  'handle',
  'lockAxis',
  'disabled',
  'sortable',
  'draggable',
  'animation',
  'autoScroll',
  'ghostClass',
  'ghostStyle',
  'chosenClass',
  'scrollSpeed',
  'appendToBody',
  'ghostContainer',
  'scrollThreshold',
  'delayOnTouchOnly',
  'placeholderClass',
  'dropOnAnimationEnd',
];

const VirtualAttrs = [
  'size',
  'keeps',
  'buffer',
  'scroller',
  'direction',
  'debounceTime',
  'throttleTime',
];

class VirtualSortable<T> {
  private el: HTMLElement;
  private options: Options<T>;
  private removeDraggedEl!: boolean;

  public virtual!: Virtual<T>;
  public sortable!: Sortable;
  public sortableState!: 'dragging' | 'choosing' | '';

  constructor(el: HTMLElement, options: Options<T>) {
    this.el = el;
    this.options = options;

    this.initVirtual();
    this.initSortable();
  }

  public destroy() {
    this.virtual.removeScrollEventListener();
    this.sortable.destroy();
  }

  public option<K extends keyof Options<T>>(key: K, value: Options<T>[K]) {
    this.options[key] = value;

    if (VirtualAttrs.includes(key) || key === 'uniqueKeys') {
      this.virtual.option(key as keyof VirtualOptions<T>, value as any);
    }

    if (SortableAttrs.includes(key)) {
      this.sortable.option(key as keyof DndOptions, value);
    }
  }

  public call<K extends MethodNames<Virtual<T>>>(
    method: K,
    ...args: Parameters<MethodType<Virtual<T>, K>>
  ): ReturnType<MethodType<Virtual<T>, K>> {
    if (method in this.virtual) {
      const func = this.virtual[method];

      if (typeof func === 'function') {
        return (func as Function).apply(this.virtual, args) as ReturnType<MethodType<Virtual<T>, K>>;
      }

      throw new Error(`Property ${String(method)} is not a function on Virtual.`);
    } else {
      throw new Error(`Method ${String(method)} does not exist on Virtual.`);
    }
  }

  // ========================================== virtual ==========================================
  private initVirtual() {
    const props = VirtualAttrs.reduce((res, key) => {
      res[key] = this.options[key];
      return res;
    }, {} as VirtualOptions<T>);

    this.virtual = new Virtual({
      ...props,
      wrapper: this.options.wrapper,
      uniqueKeys: this.options.uniqueKeys,
      onScroll: (event: ScrollEvent) => this.onScroll(event),
      onUpdate: (range: Range, changed: boolean) => this.onUpdate(range, changed),
    });
  }

  private onScroll(event: ScrollEvent) {
    this.dispatchEvent('onScroll', event);
  }

  private onUpdate(range: Range, changed: boolean) {
    if (this.sortableState === 'dragging' && changed) {
      this.removeDraggedEl = true;
    }

    this.dispatchEvent('onUpdate', range, changed);
  }

  // ========================================== sortable ==========================================
  private initSortable() {
    const props = SortableAttrs.reduce((res, key) => {
      res[key] = this.options[key];
      return res;
    }, {});

    this.sortable = new Sortable(this.el, {
      ...props,
      emptyInsertThreshold: 0,
      swapOnDrop: false,
      removeCloneOnDrop: (event) => event.from === event.to,
      onDrag: (event) => this.onDrag(event),
      onDrop: (event) => this.onDrop(event),
    });
  }

  private onDrag(event: SortableEvent) {
    this.sortableState = 'dragging';

    const key = event.node.getAttribute('data-key') as any;
    const index = this.getIndex(key);

    // store the dragged item
    this.sortable.option('store', { key, index });
    this.dispatchEvent('onDrag', { key, index, event });
  }

  private onDrop(event: SortableEvent) {
    const { key, index } = Sortable.get(event.from)!.option('store');

    const evt: DropEvent<T> = {
      key,
      event,
      changed: false,
      oldIndex: index,
      newIndex: index,
    };

    if (!(event.from === event.to && event.node === event.target)) {
      const eventProps = this.getEventProperties(evt, event);

      Object.assign(evt, eventProps);
    }

    this.dispatchEvent('onDrop', evt);

    if (event.from === this.el && this.removeDraggedEl) {
      Sortable.dragged?.remove();
    }

    if (event.from !== event.to) {
      Sortable.clone?.remove();
    }

    this.sortableState = '';
    this.removeDraggedEl = false;
  }

  private getEventProperties(evt: DropEvent<T>, event: SortableEvent) {
    const key = evt.key;
    const index = evt.oldIndex;
    const targetKey = event.target.getAttribute('data-key') as any;

    let newIndex = -1;
    let oldIndex = index;

    // changes position in current list
    if (event.from === event.to) {
      // re-get the dragged element's index
      oldIndex = this.getIndex(key);
      newIndex = this.getIndex(targetKey);

      if (
        (oldIndex < newIndex && event.relative === -1) ||
        (oldIndex > newIndex && event.relative === 1)
      ) {
        newIndex += event.relative;
      }
    } else {
      // removed from current list
      if (event.from === this.el) {
        oldIndex = this.getIndex(key);
      }

      // added to another list
      if (event.to === this.el) {
        oldIndex = -1;
        newIndex = this.getIndex(targetKey);

        if (event.relative === 0) {
          // added to last
          newIndex = this.options.uniqueKeys.length;
        } else if (event.relative === 1) {
          newIndex += event.relative;
        }
      }
    }

    return {
      changed: event.from !== event.to || newIndex !== oldIndex,
      oldIndex,
      newIndex,
    };
  }

  private getIndex(key: T) {
    if (key === null || key === undefined) {
      return -1;
    }

    const { uniqueKeys } = this.options;

    for (let i = 0, len = uniqueKeys.length; i < len; i++) {
      if (isEqual(uniqueKeys[i], key)) {
        return i;
      }
    }

    return -1;
  }

  private dispatchEvent<
    K extends OptionMethodNames<Options<T>> & ('onDrag' | 'onDrop' | 'onScroll' | 'onUpdate')
  >(name: K, ...args: Parameters<NonNullable<Options<T>[K]>>) {
    // call only when the option exists
    if (name in this.options) {
      const func = this.options[name];

      if (typeof func === 'function') {
        return (func as (...a: unknown[]) => unknown).apply(this, args);
      }
    }
  }
}

export * from './utils';
export * from './types';

export { Virtual, SortableAttrs, VirtualAttrs, VirtualSortable };
