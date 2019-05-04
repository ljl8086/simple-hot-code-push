var exec = require('cordova/exec');

exports.showToast = function (arg0, success, error) {
    exec(success, error, 'SimpleHotCodePush', 'showToast', [arg0]);
};
