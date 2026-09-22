const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = (env, argv) => {
  return {
    entry: './src/ts/index.ts',
    devtool: 'inline-source-map',
    module: {
      rules: [
        {
          test: /\.ts$/,
          include: path.resolve(__dirname, 'src/ts'),
          use: 'ts-loader',
          exclude: /node_modules/,
        },
      ],
    },
    resolve: {
      extensions: ['.ts', '.js'],
    },
    output: {
      filename: 'bundle-[contenthash:6].js',
      path: path.resolve(__dirname, 'dist'),
      clean: true,
    },
    plugins: [
      new HtmlWebpackPlugin({
        title: 'MOM',
        template: './src/html/index.html',
      }),
    ],
  };
};
