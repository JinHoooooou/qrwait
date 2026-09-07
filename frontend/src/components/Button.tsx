import { type ButtonHTMLAttributes } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary'
}

const styles = {
  base: {
    width: '100%',
    minHeight: '44px',
    padding: '0.75rem 1.5rem',
    borderRadius: '0.5rem',
    fontSize: '1rem',
    fontWeight: 600,
    border: 'none',
    cursor: 'pointer',
  } as React.CSSProperties,
  primary: {
    backgroundColor: '#3b82f6',
    color: '#ffffff',
  } as React.CSSProperties,
  secondary: {
    backgroundColor: '#f3f4f6',
    color: '#374151',
  } as React.CSSProperties,
  disabled: {
    backgroundColor: '#d1d5db',
    color: '#6b7280',
    cursor: 'not-allowed',
  } as React.CSSProperties,
}

function Button({ variant = 'primary', style, disabled, ...props }: ButtonProps) {
  return (
    <button
      disabled={disabled}
      style={{ ...styles.base, ...styles[variant], ...(disabled ? styles.disabled : null), ...style }}
      {...props}
    />
  )
}

export default Button
