import type { InputHTMLAttributes } from "react";

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
};

export function Input({ label, error, id, className = "", ...props }: InputProps) {
  const inputId = id ?? props.name;

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={inputId} className="text-sm font-medium text-brand-text">
        {label}
      </label>
      <input
        id={inputId}
        className={`rounded-lg border border-black/10 bg-brand-white px-3 py-2 text-brand-text placeholder:text-brand-text/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-orange ${className}`}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${inputId}-error` : undefined}
        {...props}
      />
      {error ? (
        <p id={`${inputId}-error`} className="text-sm text-red-700" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
