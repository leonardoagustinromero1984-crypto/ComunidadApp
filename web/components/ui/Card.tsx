import type { HTMLAttributes } from "react";

type CardProps = HTMLAttributes<HTMLDivElement>;

export function Card({ className = "", ...props }: CardProps) {
  return (
    <div
      className={`rounded-2xl border border-black/5 bg-brand-white p-6 shadow-sm ${className}`}
      {...props}
    />
  );
}
