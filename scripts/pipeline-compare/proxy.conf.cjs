module.exports = {
  '/api/**': {
    target: `http://127.0.0.1:${process.env.PIPELINE_API_PORT}`,
    secure: false,
    changeOrigin: true,
    pathRewrite: { '^/api': '/ai-sentinel/api' },
  },
};
