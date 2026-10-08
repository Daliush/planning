import React from "react";

type Variant = "magenta" | "secondary" | "danger" | "ghost";

const styles: Record<Variant, string> = {
  magenta: "bg-[#C2185B] text-white hover:bg-[#A9144F] focus-visible:outline-[#C2185B]",
  secondary: "app-surface-muted border app-divider hover:opacity-80",
  danger: "bg-red-600 text-white hover:bg-red-700",
  ghost: "bg-transparent app-accent hover:opacity-70",
};

export function Button(props: {
  children: React.ReactNode;
  onClick?: () => void;
  type?: "button" | "submit";
  disabled?: boolean;
  variant?: Variant;
  className?: string;
  title?: string;
}) {
  const variant = props.variant ?? "magenta";
  return (
    <button
      type={props.type ?? "button"}
      onClick={props.onClick}
      disabled={props.disabled}
      title={props.title}
      className={[
        "inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2",
        styles[variant],
        props.disabled ? "cursor-not-allowed opacity-60" : "",
        props.className ?? "",
      ].join(" ")}
    >
      {props.children}
    </button>
  );
}
