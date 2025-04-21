# generate_captcha.py
import sys
import os
from captcha.image import ImageCaptcha

def generate_captcha(text, output_dir, filename):
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, f"{filename}.png")

    if os.path.exists(output_path):
        print(f"验证码已存在: {output_path}")
        return

    try:
        image = ImageCaptcha(width=160, height=60)
        image.write(text, output_path)
        print(f"验证码图片已保存为: {output_path}")
    except Exception as e:
        print(f"生成验证码 {text} 失败: {e}")
        sys.exit(2)

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("用法: python generate_captcha.py <验证码文本> <输出目录> <文件名>")
        sys.exit(1)

    captcha_text = sys.argv[1]
    output_dir = sys.argv[2]
    filename = sys.argv[3]
    generate_captcha(captcha_text, output_dir, filename)
