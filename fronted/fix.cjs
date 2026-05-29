const fs = require('fs');
let content = fs.readFileSync('src/assets/layout.scss', 'utf-8');
content = content.replace('$star\\-duration: math.div(\\-duration, 2);', '$star-duration: math.div($star-duration, 2);');
content = content.replaceAll('unquote(', 'string.unquote(');
content = content.replace("@use 'sass:math';", "@use 'sass:math';\n@use 'sass:string';");
fs.writeFileSync('src/assets/layout.scss', content, 'utf-8');
