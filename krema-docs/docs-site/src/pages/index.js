import clsx from 'clsx';
import {useEffect, useRef, useState} from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import CodeBlock from '@theme/CodeBlock';
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
import {
  LeafIcon,
  CoffeeIcon,
  BrowserIcon,
  DevicesIcon,
  LightningIcon,
  RocketIcon,
} from '../components/Icons';
import styles from './index.module.css';

/* ===== Scroll fade-in hook ===== */
function useFadeInOnScroll() {
  const ref = useRef(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          el.classList.add('visible');
          observer.unobserve(el);
        }
      },
      {threshold: 0.15},
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  return ref;
}

/* ===== Terminal Window wrapper ===== */
function TerminalWindow({title, children}) {
  return (
    <div className={styles.terminalWindow}>
      <div className={styles.terminalWindowTitleBar}>
        <span className={clsx(styles.terminalDot, styles.terminalDotRed)} />
        <span className={clsx(styles.terminalDot, styles.terminalDotYellow)} />
        <span className={clsx(styles.terminalDot, styles.terminalDotGreen)} />
        {title && <span className={styles.terminalWindowTitle}>{title}</span>}
      </div>
      {children}
    </div>
  );
}

/* ===== Hero ===== */
const heroInstallOptions = [
  {id: 'npm', label: 'npm', commands: [
    {text: 'npm install -g krema'},
    {text: 'krema init my-app --template react'},
  ]},
  {id: 'npx', label: 'npx', commands: [
    {text: 'npx krema init my-app --template react'},
  ]},
  {id: 'curl', label: 'curl', commands: [
    {text: 'curl -fsSL https://krema.build/install.sh | bash'},
    {text: 'krema init my-app --template react'},
  ]},
];

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  const [activeTab, setActiveTab] = useState('npm');
  const active = heroInstallOptions.find((o) => o.id === activeTab);

  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <Heading as="h1" className={styles.heroTitle}>
          {siteConfig.title}
        </Heading>
        <p className={styles.heroSubtitle}>{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link className={styles.btnPrimary} to="/docs/getting-started/installation">
            Get Started
          </Link>
          <Link className={styles.btnSecondary} to="https://github.com/krema-build/krema">
            GitHub
          </Link>
        </div>
        <div className={styles.heroTerminal}>
          <div className={styles.terminalTitleBar}>
            <span className={clsx(styles.terminalDot, styles.terminalDotRed)} />
            <span className={clsx(styles.terminalDot, styles.terminalDotYellow)} />
            <span className={clsx(styles.terminalDot, styles.terminalDotGreen)} />
          </div>
          <div className={styles.heroTerminalTabs}>
            {heroInstallOptions.map((opt) => (
              <button
                key={opt.id}
                className={clsx(styles.heroTab, activeTab === opt.id && styles.heroTabActive)}
                onClick={() => setActiveTab(opt.id)}>
                {opt.label}
              </button>
            ))}
          </div>
          <div className={styles.terminalBody}>
            {active.commands.map((cmd, i) => (
              <div key={i}>
                <span className={styles.terminalPrompt}>$ </span>
                <span className={styles.terminalCommand}>{cmd.text}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </header>
  );
}

/* ===== Features ===== */
const FeatureList = [
  {
    title: 'Lightweight',
    Icon: LeafIcon,
    description:
      '50-80MB bundle size. Uses system WebViews instead of bundling Chromium. Lower memory footprint than Electron.',
  },
  {
    title: 'Java Backend',
    Icon: CoffeeIcon,
    description:
      'Write backend logic in Java with access to the entire Java ecosystem. Maven, Spring, and thousands of libraries at your fingertips.',
  },
  {
    title: 'Web Frontend',
    Icon: BrowserIcon,
    description:
      'Build your UI with any web framework: React, Vue, Svelte, or vanilla HTML/CSS/JS. Full TypeScript support included.',
  },
  {
    title: 'Cross-Platform',
    Icon: DevicesIcon,
    description:
      'Build once, run on macOS, Windows, and Linux. Native look and feel on each platform with system WebViews.',
  },
  {
    title: 'Native APIs',
    Icon: LightningIcon,
    description:
      'File dialogs, clipboard, notifications, system tray, global shortcuts, and more. All accessible from your frontend.',
  },
  {
    title: 'Easy Distribution',
    Icon: RocketIcon,
    description:
      'Built-in bundling, code signing, notarization, and auto-updates. Ship to all platforms with a single command.',
  },
];

function Feature({Icon, title, description}) {
  return (
    <div className={styles.featureCard}>
      <div className={styles.featureIconBox}>
        <Icon />
      </div>
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}

function HomepageFeatures() {
  const ref = useFadeInOnScroll();
  return (
    <section className={styles.features}>
      <div className="container">
        <Heading as="h2" className={styles.sectionHeading}>
          Modern desktop apps. Cross platform.<br />Java backend. Web frontend.
        </Heading>
        <div className={clsx('fadeInOnScroll', styles.featureGrid)} ref={ref}>
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}

/* ===== Benefits (replaces ComparisonTable) ===== */
const BenefitsList = [
  {
    title: 'Java Backend Ecosystem',
    description: 'Maven, Spring, and thousands of battle-tested libraries. Use the tools Java developers already know and trust.',
  },
  {
    title: 'Any Web Framework',
    description: 'Build your UI with React, Vue, Svelte, or anything that runs in a browser. Full TypeScript support included.',
  },
  {
    title: 'Lightweight by Design',
    description: 'Uses the system WebView instead of bundling Chromium. Small downloads, low memory footprint.',
  },
  {
    title: 'Ready to Ship',
    description: 'CLI scaffolding, hot reload, cross-platform bundling, code signing, and auto-updates — all out of the box.',
  },
];

function BenefitsSection() {
  const ref = useFadeInOnScroll();
  return (
    <section className={styles.benefits}>
      <div className="container">
        <Heading as="h2" className={styles.sectionHeading}>
          Why Krema?
        </Heading>
        <div className={clsx('fadeInOnScroll', styles.benefitsGrid)} ref={ref}>
          {BenefitsList.map(({title, description}, idx) => (
            <div key={idx} className={styles.benefitCard}>
              <div className={styles.benefitTitle}>{title}</div>
              <p className={styles.benefitDesc}>{description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ===== Works With ===== */
function ReactLogo() {
  return (
    <svg viewBox="0 0 36 36" fill="none">
      <circle cx="18" cy="18" r="3.5" fill="#61DAFB" />
      <ellipse cx="18" cy="18" rx="16" ry="6" stroke="#61DAFB" strokeWidth="1.5" />
      <ellipse cx="18" cy="18" rx="16" ry="6" stroke="#61DAFB" strokeWidth="1.5" transform="rotate(60 18 18)" />
      <ellipse cx="18" cy="18" rx="16" ry="6" stroke="#61DAFB" strokeWidth="1.5" transform="rotate(120 18 18)" />
    </svg>
  );
}

function VueLogo() {
  return (
    <svg viewBox="0 0 36 36" fill="none">
      <path d="M22.2 4H29L18 22L7 4H13.8L18 11L22.2 4Z" fill="#41B883" />
      <path d="M7 4L18 22L29 4H25L18 16L11 4H7Z" fill="#34495E" />
    </svg>
  );
}

function SvelteLogo() {
  return (
    <svg viewBox="0 0 36 36" fill="none">
      <path d="M28 7.5C25 2.5 18.5 1 14 4.5L8.5 8.5C6.5 10 5.5 12 5.5 14.2C5.5 15.5 5.9 16.8 6.5 17.8C5.8 19 5.5 20.3 5.5 21.8C5.5 24 6.5 26 8 27.5C11 32.5 17.5 34 22 30.5L27.5 26.5C29.5 25 30.5 23 30.5 20.8C30.5 19.5 30.1 18.2 29.5 17.2C30.2 16 30.5 14.7 30.5 13.2C30.5 11 29.5 9 28 7.5Z" fill="#FF3E00" />
      <path d="M15.5 29C13 29.5 10.5 28 9.5 25.5C9 24.5 9 23.5 9.2 22.5L9.5 21.5L10 22C11 23 12.2 23.5 13.5 23.8L14 23.8L14 24.2C14 25 14.5 25.8 15.2 26.2C16 26.6 17 26.6 17.8 26.2L23 22.5C23.5 22.2 23.8 21.7 23.8 21.2C23.8 20.7 23.7 20.2 23.3 19.8C22.5 19.4 21.5 19.4 20.8 19.8L18.5 21.2C16.5 22.5 14 22.5 12 21.5C10 20.5 9 18.5 9 16.2C9 14.5 9.8 13 11 11.8L16.5 8C17.5 7.3 18.5 7 19.8 7C22.3 6.5 24.8 8 25.8 10.5C26.3 11.5 26.3 12.5 26 13.5L25.8 14.5L25.2 14C24.2 13 23 12.5 21.8 12.2L21.2 12.2L21.2 11.8C21.2 11 20.8 10.2 20 9.8C19.2 9.4 18.2 9.4 17.5 9.8L12.2 13.5C11.8 13.8 11.5 14.3 11.5 14.8C11.5 15.3 11.7 15.8 12 16.2C12.8 16.6 13.8 16.6 14.5 16.2L16.8 14.8C18.8 13.5 21.2 13.5 23.2 14.5C25.2 15.5 26.2 17.5 26.2 19.8C26.2 21.5 25.4 23 24.2 24.2L19 27.8C18 28.5 17 29 15.5 29Z" fill="white" />
    </svg>
  );
}

function AngularLogo() {
  return (
    <svg viewBox="0 0 36 36" fill="none">
      <path d="M18 3L4 8.5L6.5 27L18 33.5L29.5 27L32 8.5L18 3Z" fill="#DD0031" />
      <path d="M18 3V33.5L29.5 27L32 8.5L18 3Z" fill="#C3002F" />
      <path d="M18 7L10 26H13L14.5 22H21.5L23 26H26L18 7ZM18 13L20.5 20H15.5L18 13Z" fill="white" />
    </svg>
  );
}

function TypeScriptLogo() {
  return (
    <svg viewBox="0 0 36 36" fill="none">
      <rect x="3" y="3" width="30" height="30" rx="3" fill="#3178C6" />
      <path d="M15 17H10V15H22V17H17V27H15V17Z" fill="white" />
      <path d="M22 27V20C22 18 23.5 17 25.5 17C26.5 17 27.2 17.3 27.8 17.8L27 19.2C26.5 18.8 26 18.5 25.5 18.5C24.5 18.5 24 19.2 24 20V27H22Z" fill="white" />
    </svg>
  );
}

function JavaLogo() {
  return (
    <svg viewBox="0 0 36 36" fill="none">
      <path d="M14 23.5C14 23.5 12.5 24.5 15 24.8C18 25.2 19.5 25.1 23 24.3C23 24.3 23.8 24.8 24.8 25.2C19 27.5 11.5 25 14 23.5Z" fill="#5382A1" />
      <path d="M13 20.5C13 20.5 11.3 21.8 14 22C17.2 22.3 19.8 22.3 24 21C24 21 24.5 21.7 25.5 22C18.5 24.5 10 22.2 13 20.5Z" fill="#5382A1" />
      <path d="M19.5 16C21.2 18 19 19.8 19 19.8C19 19.8 23 17.8 21 15.2C19.2 12.8 17.8 11.5 25 7.5C25 7.5 14.5 10.2 19.5 16Z" fill="#E76F00" />
      <path d="M26 25.5C26 25.5 27 26.3 25 27C21 28.5 12 28.8 9 27C8 26.5 10 25.7 10.8 25.5C11.5 25.3 12 25.4 12 25.4C10.8 24.5 5 27.5 9.5 28.2C20 29.8 29 26.8 26 25.5Z" fill="#5382A1" />
    </svg>
  );
}

function MavenLogo() {
  return (
    <svg viewBox="0 0 36 36" fill="none">
      <rect x="4" y="8" width="28" height="20" rx="3" fill="#C62828" />
      <text x="18" y="22" textAnchor="middle" fill="white" fontSize="9" fontWeight="bold" fontFamily="sans-serif">mvn</text>
    </svg>
  );
}

function SpringLogo() {
  return (
    <svg viewBox="0 0 36 36" fill="none">
      <circle cx="18" cy="18" r="15" fill="#6DB33F" />
      <path d="M28 8C27 12 24 15 21 17C24 19 26 22 26 26L24 28C24 23 21 19 18 18C15 19 12 23 12 28L10 26C10 22 12 19 15 17C12 15 9 12 8 8L10 6C11 11 14 15 18 16C22 15 25 11 26 6L28 8Z" fill="white" />
    </svg>
  );
}

function GraalVMLogo() {
  return (
    <svg viewBox="0 0 36 36" fill="none">
      <circle cx="18" cy="18" r="15" fill="#19BFBC" />
      <path d="M11 12L18 8L25 12V18L18 28L11 18V12Z" fill="white" fillOpacity="0.9" />
      <path d="M18 8L25 12V18L18 28V8Z" fill="white" fillOpacity="0.7" />
    </svg>
  );
}

function WorksWithSection() {
  const ref = useFadeInOnScroll();
  return (
    <section className={styles.worksWith}>
      <div className="container">
        <Heading as="h2" className={styles.sectionHeading}>
          Build with the tools you know
        </Heading>
        <div className={clsx('fadeInOnScroll')} ref={ref}>
          <div className={styles.worksWithRow}>
            <span className={styles.worksWithLabel}>Frontend</span>
            <div className={styles.worksWithLogos}>
              <div className={styles.logoItem}><ReactLogo /><span>React</span></div>
              <div className={styles.logoItem}><VueLogo /><span>Vue</span></div>
              <div className={styles.logoItem}><SvelteLogo /><span>Svelte</span></div>
              <div className={styles.logoItem}><AngularLogo /><span>Angular</span></div>
              <div className={styles.logoItem}><TypeScriptLogo /><span>TypeScript</span></div>
            </div>
          </div>
          <div className={styles.worksWithRow}>
            <span className={styles.worksWithLabel}>Backend</span>
            <div className={styles.worksWithLogos}>
              <div className={styles.logoItem}><JavaLogo /><span>Java</span></div>
              <div className={styles.logoItem}><MavenLogo /><span>Maven</span></div>
              <div className={styles.logoItem}><GraalVMLogo /><span>GraalVM</span></div>
              <div className={styles.logoItem}><SpringLogo /><span>Spring</span></div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

/* ===== Quick Start ===== */
function QuickStartSection() {
  const ref = useFadeInOnScroll();

  const npmCode = `# Install globally
npm install -g krema

# Create a new project
krema init my-app --template react

# Start development
cd my-app && krema dev`;

  const npxCode = `# No install needed
npx krema init my-app --template react

# Start development
cd my-app && npx krema dev`;

  const curlCode = `# Install via script
curl -fsSL https://raw.githubusercontent.com/krema-build/krema/master/packages/install.sh | bash

# Create a new project
krema init my-app --template react

# Start development
cd my-app && krema dev`;

  return (
    <section className={styles.quickstart}>
      <div className={clsx('fadeInOnScroll', styles.quickstartContent)} ref={ref}>
        <div className={styles.quickstartText}>
          <Heading as="h2">Get started in seconds</Heading>
          <p>
            Krema CLI scaffolds your project with everything you need:
            frontend framework, Java backend, and build configuration.
          </p>
          <p>
            Choose from templates for React, Vue, Svelte, or vanilla JavaScript.
            Hot reload works out of the box for rapid development.
          </p>
          <Link className={styles.btnPrimary} to="/docs/getting-started/quick-start">
            Read the Quick Start Guide
          </Link>
        </div>
        <div className={styles.quickstartCode}>
          <TerminalWindow title="Terminal">
            <Tabs>
              <TabItem value="npm" label="npm" default>
                <CodeBlock language="bash">{npmCode}</CodeBlock>
              </TabItem>
              <TabItem value="npx" label="npx">
                <CodeBlock language="bash">{npxCode}</CodeBlock>
              </TabItem>
              <TabItem value="curl" label="curl">
                <CodeBlock language="bash">{curlCode}</CodeBlock>
              </TabItem>
            </Tabs>
          </TerminalWindow>
        </div>
      </div>
    </section>
  );
}

/* ===== Backend Code ===== */
function BackendCodeSection() {
  const ref = useFadeInOnScroll();

  const javaCode = `@KremaCommand
public String greet(String name) {
    return "Hello, " + name + "!";
}

@KremaCommand
@RequiresPermission(Permission.FS_READ)
public String readFile(String path) throws IOException {
    return Files.readString(Path.of(path));
}`;

  const jsCode = `// Call backend commands
const greeting = await window.krema.invoke('greet', {
  name: 'World'
});

// Listen for backend events
window.krema.on('file-changed', (data) => {
  console.log('File changed:', data.path);
});`;

  return (
    <section className={styles.codeSection}>
      <div className="container">
        <Heading as="h2" className={styles.sectionHeading}>
          Simple IPC, powerful results
        </Heading>
        <div className={clsx('fadeInOnScroll', styles.codeSectionGrid)} ref={ref}>
          <TerminalWindow title="App.java">
            <CodeBlock language="java">{javaCode}</CodeBlock>
          </TerminalWindow>
          <TerminalWindow title="app.js">
            <CodeBlock language="javascript">{jsCode}</CodeBlock>
          </TerminalWindow>
        </div>
      </div>
    </section>
  );
}

/* ===== Page ===== */
export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title="Build desktop apps with Java + Web"
      description="Krema is a lightweight desktop application framework using Java backends and web frontends with system WebViews">
      <HomepageHeader />
      <main>
        <HomepageFeatures />
        <BenefitsSection />
        <WorksWithSection />
        <QuickStartSection />
        <BackendCodeSection />
      </main>
    </Layout>
  );
}
