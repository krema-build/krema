import React, {useState, useEffect, useRef} from 'react';
import {useColorMode} from '@docusaurus/theme-common';
import clsx from 'clsx';
import styles from './styles.module.css';

const THEMES = [
  {id: 'light', label: 'Light'},
  {id: 'dark', label: 'Dark'},
  {id: 'navy', label: 'Navy'},
];

function ThemeIcon({theme, size = 18}) {
  const props = {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 2,
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
  };

  switch (theme) {
    case 'light':
      return (
        <svg {...props}>
          <circle cx="12" cy="12" r="5" />
          <line x1="12" y1="1" x2="12" y2="3" />
          <line x1="12" y1="21" x2="12" y2="23" />
          <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
          <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
          <line x1="1" y1="12" x2="3" y2="12" />
          <line x1="21" y1="12" x2="23" y2="12" />
          <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
          <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
        </svg>
      );
    case 'dark':
      return (
        <svg {...props}>
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
      );
    case 'navy':
      return (
        <svg {...props}>
          <circle cx="12" cy="5" r="3" />
          <line x1="12" y1="8" x2="12" y2="21" />
          <path d="M5 12H2a10 10 0 0 0 20 0h-3" />
        </svg>
      );
    default:
      return null;
  }
}

export default function ColorModeToggle({className}) {
  const {setColorMode} = useColorMode();
  const [theme, setTheme] = useState('light');
  const [isOpen, setIsOpen] = useState(false);
  const ref = useRef(null);
  const initialized = useRef(false);

  // Initialize from localStorage
  useEffect(() => {
    const saved = localStorage.getItem('krema-theme');
    if (saved && THEMES.some((t) => t.id === saved)) {
      setTheme(saved);
    } else {
      const current =
        document.documentElement.getAttribute('data-theme') || 'light';
      setTheme(current);
    }
    initialized.current = true;
  }, []);

  // Apply theme changes
  useEffect(() => {
    if (!initialized.current) return;

    if (theme === 'navy') {
      document.documentElement.setAttribute('data-theme', 'dark');
      document.documentElement.setAttribute('data-krema-theme', 'navy');
      setColorMode('dark');
    } else {
      document.documentElement.setAttribute('data-theme', theme);
      document.documentElement.removeAttribute('data-krema-theme');
      setColorMode(theme);
    }
    localStorage.setItem('krema-theme', theme);
  }, [theme]);

  // Close on outside click
  useEffect(() => {
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // Close on Escape
  useEffect(() => {
    const handler = (e) => {
      if (e.key === 'Escape') setIsOpen(false);
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, []);

  return (
    <div className={clsx(styles.wrapper, className)} ref={ref}>
      <button
        className={styles.toggle}
        onClick={() => setIsOpen((prev) => !prev)}
        aria-label={`Theme: ${theme}`}
        aria-expanded={isOpen}
      >
        <ThemeIcon theme={theme} />
      </button>
      {isOpen && (
        <ul className={styles.dropdown} role="listbox">
          {THEMES.map(({id, label}) => (
            <li key={id} role="option" aria-selected={id === theme}>
              <button
                className={clsx(
                  styles.option,
                  id === theme && styles.optionActive,
                )}
                onClick={() => {
                  setTheme(id);
                  setIsOpen(false);
                }}
              >
                <ThemeIcon theme={id} size={16} />
                <span>{label}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
