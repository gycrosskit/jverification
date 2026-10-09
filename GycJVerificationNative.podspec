Pod::Spec.new do |s|
  s.name = 'GycJVerificationNative'
  s.version = '0.1.5'
  s.summary = '极光一键认证 SDK 的同意、预取号、授权与释放边界'
  s.homepage = 'https://github.com/gycrosskit/jverification'
  s.license = { :type => 'Apache-2.0', :file => 'LICENSE' }
  s.author = 'GY CrossKit'
  s.source = { :git => 'https://github.com/gycrosskit/jverification.git', :tag => s.version.to_s }
  s.ios.deployment_target = '15.0'
  s.swift_version = '5.9'
  # 厂商 XCFramework 含静态库，组件不能作为动态 framework 传递链接它们。
  s.static_framework = true
  s.default_subspec = 'Native'
  s.subspec 'Native' do |native|
    native.source_files = 'iosApp/Sources/GycJVerificationNative/*.{swift,h}'
    native.public_header_files = 'iosApp/Sources/GycJVerificationNative/*.h'
    native.dependency 'JVerification', '3.4.7'
    native.dependency 'JCore', '5.5.1'
  end
  s.subspec 'Kuikly' do |kuikly|
    kuikly.dependency 'GycJVerificationNative/Native'
    kuikly.dependency 'OpenKuiklyIOSRender', '2.28.0'
    kuikly.source_files = 'iosApp/Sources/GycJVerificationKuikly/*.swift'
  end
end
