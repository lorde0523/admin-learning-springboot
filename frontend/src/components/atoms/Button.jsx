/** Native button wrapper with consistent visual variants. */
export const Button = ({ variant = 'primary', className = '', ...props }) => (
  <button className={`button button--${variant} ${className}`.trim()} {...props} />
);

