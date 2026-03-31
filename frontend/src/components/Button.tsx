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
}

function Button({ variant = 'primary', style, ...props }: ButtonProps) {
  return (
    <button
      style={{ ...styles.base, ...styles[variant], ...style }}
      {...props}
    />
  )
}

export default Button
