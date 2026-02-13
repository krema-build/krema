// @ts-check

/**
 * Krema documentation sidebar configuration
 * @type {import('@docusaurus/plugin-content-docs').SidebarsConfig}
 */
const sidebars = {
  docsSidebar: [
    {
      type: 'category',
      label: 'Getting Started',
      collapsed: false,
      items: [
        'getting-started/installation',
        'getting-started/quick-start',
        'getting-started/project-structure',
      ],
    },
    {
      type: 'category',
      label: 'Guides',
      items: [
        'guides/error-handling',
        'guides/plugins',
        'guides/building',
        'guides/native-image',
        'guides/code-signing',
        'guides/auto-updates',
      ],
    },
    {
      type: 'category',
      label: 'API Reference',
      items: [
        'api/javascript-api',
        'api/krema-toml',
        'api/cli-reference',
        'api/cli-cheatsheet',
        {
          type: 'category',
          label: 'Core APIs',
          collapsed: true,
          items: [
            'api/window-advanced',
            'api/menu-shortcut',
            'api/os-info',
            'api/dock',
            'api/single-instance',
            'api/app-environment',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: 'Plugins',
      items: [
        'plugins/overview',
        'plugins/sql',
        'plugins/websocket',
        'plugins/upload',
        'plugins/positioner',
        'plugins/autostart',
      ],
    },
    {
      type: 'category',
      label: 'Migration',
      items: [
        'migration/from-electron',
        'migration/from-tauri',
      ],
    },
    {
      type: 'category',
      label: 'Advanced',
      items: [
        'advanced/security',
        'advanced/troubleshooting',
        'advanced/architecture',
      ],
    },
  ],
};

export default sidebars;
