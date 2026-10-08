import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** Tailwind 类名合并 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
